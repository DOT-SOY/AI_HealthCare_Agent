import { useState, useEffect } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { confirmTossPayment } from '../../services/orderApi';
import { useCart } from '../../components/layout/ShopLayout';

const MAX_RETRY = 5;

const PaymentSuccessPage = () => {
  const [searchParams] = useSearchParams();
  const [status, setStatus] = useState('loading'); // 'loading' | 'success' | 'error'
  const [detail, setDetail] = useState(null);
  const [errorMessage, setErrorMessage] = useState('');
  const [retryCount, setRetryCount] = useState(0);
  const { resetCart } = useCart();

  useEffect(() => {
    const paymentKey = searchParams.get('paymentKey');
    const orderId = searchParams.get('orderId');
    const amountParam = searchParams.get('amount');

    if (!paymentKey || !orderId || amountParam == null || amountParam === '') {
      setStatus('error');
      setErrorMessage('결제 정보가 없습니다. (paymentKey, orderId, amount)');
      return;
    }

    const amountNum = parseInt(amountParam, 10);
    if (!Number.isFinite(amountNum) || amountNum < 1) {
      setStatus('error');
      setErrorMessage('결제 금액이 올바르지 않습니다.');
      return;
    }

    let cancelled = false;
    const run = async () => {
      try {
        const res = await confirmTossPayment({
          paymentKey,
          orderId,
          amount: amountNum,
        });
        if (cancelled) return;
        setDetail(res);
        setStatus('success');
        if (typeof resetCart === 'function') {
          try {
            await resetCart('payment_success_confirmed');
          } catch (e) {
            console.error('Failed to reset cart after payment success', e);
          }
        }
      } catch (err) {
        if (cancelled) return;
        const msg = err?.message ?? '';
        const isProcessing = /기존 요청을 처리중|S008|FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING|처리 중입니다/i.test(msg);
        const isAlreadyProcessed = /이미 처리된 결제|ALREADY_PROCESSED_PAYMENT/i.test(msg);
        if (isAlreadyProcessed) {
          // 이미 승인 완료된 결제라면, 주문 상태는 DB 상으로는 PAID 이므로
          // 프론트에서도 동일하게 간주해서 주문서 상세 페이지로 이동할 수 있게 한다.
          setDetail({ orderId, amount: amountNum, orderStatus: 'PAID' });
          setStatus('success');
          if (typeof resetCart === 'function') {
            try {
              await resetCart('payment_already_processed');
            } catch (e) {
              console.error('Failed to reset cart after already-processed payment', e);
            }
          }
          return;
        }

        if (isProcessing && retryCount < MAX_RETRY) {
          // 토스 S008 등 "처리 중"이면 잠시 후 자동 재시도
          setStatus('loading');
          setErrorMessage('');
          setTimeout(() => {
            setRetryCount((prev) => prev + 1);
          }, 1000);
          return;
        }

        setStatus('error');
        setErrorMessage(msg || '결제 승인 중 오류가 발생했습니다.');
      }
    };
    run();
    return () => { cancelled = true; };
  }, [searchParams, retryCount]);

  if (status === 'loading') {
    return (
      <div className="max-w-lg mx-auto text-center py-16">
        <p className="text-gray-600 mb-2">결제 승인 처리 중입니다...</p>
        {retryCount > 0 && (
          <p className="text-gray-500 text-sm">
            결제 완료 여부를 확인하는 중입니다. 잠시만 기다려 주세요. (자동 재시도 {retryCount}/{MAX_RETRY})
          </p>
        )}
      </div>
    );
  }

  if (status === 'error') {
    return (
      <div className="max-w-lg mx-auto text-center py-16">
        <h1 className="text-xl font-bold text-red-600 mb-2">결제 승인 실패</h1>
        <p className="text-gray-700 mb-6">{errorMessage}</p>
        <Link to="/shop/list" className="text-blue-500 hover:underline">
          쇼핑 계속하기
        </Link>
      </div>
    );
  }

  return (
    <div className="max-w-lg mx-auto text-center py-16">
      <h1 className="text-2xl font-bold text-green-600 mb-4">결제가 완료되었습니다</h1>
      {detail && (
        <div className="text-left bg-gray-50 rounded-lg p-4 mb-6">
          <p><span className="font-medium">주문번호</span> {detail.orderId}</p>
          <p><span className="font-medium">결제금액</span> {Number(detail.amount ?? 0).toLocaleString()}원</p>
          {detail.approvedAt && (
            <p><span className="font-medium">승인일시</span> {new Date(detail.approvedAt).toLocaleString()}</p>
          )}
        </div>
      )}
      <div className="flex justify-center gap-3">
        {detail?.orderId && (
          <Link
            to={`/shop/orders/${encodeURIComponent(detail.orderId)}`}
            className="inline-block px-6 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 font-medium"
          >
            주문서 바로 보기
          </Link>
        )}
        <Link
          to="/shop/list"
          className="inline-block px-6 py-2 border rounded-lg hover:bg-gray-50 font-medium"
        >
          쇼핑 계속하기
        </Link>
      </div>
    </div>
  );
};

export default PaymentSuccessPage;
