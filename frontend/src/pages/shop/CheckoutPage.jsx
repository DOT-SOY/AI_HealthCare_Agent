import { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useCart } from '../../components/layout/ShopLayout';
import { getCart } from '../../services/cartApi';
import { createOrderFromCart, preparePayment } from '../../services/orderApi';
import { getMyAddressList } from '../../services/memberInfoAddrApi';

const TOSS_V1_URL = 'https://js.tosspayments.com/v1/payment.js';
const TOSS_V2_URL = 'https://js.tosspayments.com/v2/payment.js';
/** localhost에서 CDN 403 회피용 (vite proxy: /tosspayments-proxy → js.tosspayments.com) */
const getTossProxyUrl = () => (typeof window !== 'undefined' ? `${window.location.origin}/tosspayments-proxy/v2/standard` : '');

const loadScript = (url, runId) =>
  new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = url;
    script.async = true;
    script.onload = () => {
      resolve(window.TossPayments);
    };
    script.onerror = () => {
      reject(new Error(`Toss Payments 스크립트 로드 실패: ${url}`));
    };
    document.body.appendChild(script);
  });

const loadTossScript = () => {
  return new Promise((resolve, reject) => {
    if (typeof window !== 'undefined' && window.TossPayments) {
      resolve(window.TossPayments);
      return;
    }
    loadScript(TOSS_V1_URL, 'run')
      .then(resolve)
      .catch(() => {
        loadScript(TOSS_V2_URL, 'post-fix')
          .then(resolve)
          .catch(() => {
            const proxyUrl = getTossProxyUrl();
            return proxyUrl ? loadScript(proxyUrl, 'proxy') : Promise.reject(new Error('no proxy'));
          })
          .then(resolve)
          .catch(async () => {
            // v1/v2 CDN·프록시 모두 실패 → NPM + 프록시 src 시도 (localhost 회피용)
            try {
              const mod = await import('@tosspayments/tosspayments-sdk');
              const loadTossPayments = mod.loadTossPayments ?? mod.default;
              const proxySrc = getTossProxyUrl();
              window.TossPayments = (clientKey) => ({
                requestPayment: async (method, opts) => {
                  const sdk = await loadTossPayments(clientKey, proxySrc ? { src: proxySrc } : undefined);
                  if (!sdk?.payment?.requestPayment) {
                    throw new Error('결제 스크립트를 불러오지 못했습니다. localhost/문서키 환경에서는 토스 CDN이 차단될 수 있어, 등록된 도메인과 연동키가 필요합니다.');
                  }
                  const methodCode = method === '카드' ? 'CARD' : method;
                  const amountObj = typeof opts.amount === 'number' ? { value: opts.amount, currency: 'KRW' } : opts.amount;
                  return sdk.payment.requestPayment({
                    method: methodCode,
                    amount: amountObj,
                    orderId: opts.orderId,
                    orderName: opts.orderName,
                    successUrl: opts.successUrl,
                    failUrl: opts.failUrl,
                  });
                },
              });
              resolve(window.TossPayments);
            } catch (e) {
              reject(e);
            }
          });
      });
  });
};

const CheckoutPage = () => {
  const navigate = useNavigate();
  const { cartItems, totals } = useCart?.() ?? { cartItems: [], totals: {} };
  const [cartSummary, setCartSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [form, setForm] = useState({
    shipTo: { recipientName: '', recipientPhone: '', zipcode: '', address1: '', address2: '' },
    buyer: { buyerName: '', buyerEmail: '', buyerPhone: '' },
    memo: '',
    paymentMethod: 'CARD',
  });
  /** 결제위젯 연동 키 사용 시: 'widget_ready'면 위젯이 렌더된 뒤 결제하기 대기 */
  const [checkoutPhase, setCheckoutPhase] = useState('form');
  const [widgetOrderPayload, setWidgetOrderPayload] = useState(null);
  const widgetInstanceRef = useRef(null);

  /** 저장된 배송지 목록 (기본 배송지 우선) */
  const [addressList, setAddressList] = useState([]);
  /** 배송지 불러오기 드롭다운 표시 여부 */
  const [showAddressSelect, setShowAddressSelect] = useState(false);
  /** 기본 배송지 자동 기입 한 번만 수행 */
  const defaultAppliedRef = useRef(false);

  /** 토스 requestPayment method 코드 ↔ 화면 라벨 (API 개별 연동 키 + 결제창용) */
  const PAYMENT_METHODS = [
    { value: 'CARD', label: '카드(신용/체크/간편결제)', description: '네이버페이·카카오페이 등은 결제창에서 선택' },
    { value: 'TRANSFER', label: '계좌이체(실시간 이체)' },
    { value: 'VIRTUAL_ACCOUNT', label: '가상계좌' },
    { value: 'MOBILE_PHONE', label: '휴대폰' },
  ];

  const refreshCart = useCallback(async () => {
    try {
      const data = await getCart();
      setCartSummary(data);
    } catch {
      setCartSummary(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refreshCart();
  }, [refreshCart]);

  useEffect(() => {
    if (!localStorage.getItem('accessToken')) {
      navigate('/member/login', { state: { from: '/shop/checkout' }, replace: true });
      return;
    }
  }, [navigate]);

  /** 로그인 시 내 배송지 목록 조회 */
  useEffect(() => {
    if (!localStorage.getItem('accessToken')) return;
    getMyAddressList()
      .then((list) => setAddressList(Array.isArray(list) ? list : []))
      .catch(() => setAddressList([]));
  }, []);

  /** 저장된 기본 배송지가 있으면 한 번만 자동 기입 */
  useEffect(() => {
    if (addressList.length === 0 || defaultAppliedRef.current) return;
    const defaultAddr = addressList.find((a) => a.isDefault);
    if (defaultAddr) {
      setForm((prev) => ({
        ...prev,
        shipTo: {
          recipientName: defaultAddr.shipToName ?? '',
          recipientPhone: defaultAddr.shipToPhone ?? '',
          zipcode: defaultAddr.shipZipcode ?? '',
          address1: defaultAddr.shipAddress1 ?? '',
          address2: defaultAddr.shipAddress2 ?? '',
        },
      }));
      defaultAppliedRef.current = true;
    }
  }, [addressList]);

  const handleChange = (section, field, value) => {
    setForm((prev) => ({
      ...prev,
      [section]: { ...prev[section], [field]: value },
    }));
  };

  /** DTO 항목을 form.shipTo로 적용 (배송지 불러오기 선택 시) */
  const applyAddressToForm = (addr) => {
    if (!addr) return;
    setForm((prev) => ({
      ...prev,
      shipTo: {
        recipientName: addr.shipToName ?? '',
        recipientPhone: addr.shipToPhone ?? '',
        zipcode: addr.shipZipcode ?? '',
        address1: addr.shipAddress1 ?? '',
        address2: addr.shipAddress2 ?? '',
      },
    }));
    setShowAddressSelect(false);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (submitting) return;

    // 결제위젯 연동 키: 위젯 렌더 후 "결제하기" 두 번째 클릭 → requestPayment
    if (checkoutPhase === 'widget_ready' && widgetInstanceRef.current && widgetOrderPayload) {
      setSubmitting(true);
      try {
        await widgetInstanceRef.current.requestPayment({
          orderId: widgetOrderPayload.orderId,
          orderName: widgetOrderPayload.orderName,
          successUrl: widgetOrderPayload.successUrl,
          failUrl: widgetOrderPayload.failUrl,
          customerName: widgetOrderPayload.customerName || undefined,
          customerEmail: widgetOrderPayload.customerEmail || undefined,
          customerMobilePhone: widgetOrderPayload.customerMobilePhone || undefined,
        });
      } catch (err) {
        console.error(err);
        alert(err?.message ?? '결제 요청 중 오류가 발생했습니다.');
      } finally {
        setSubmitting(false);
      }
      return;
    }

    const items = cartSummary?.items ?? cartItems ?? [];
    if (!items.length) {
      alert('장바구니가 비어 있습니다.');
      return;
    }
    if (!form.shipTo.recipientName?.trim() || !form.shipTo.recipientPhone?.trim() || !form.shipTo.zipcode?.trim() || !form.shipTo.address1?.trim()) {
      alert('배송지(수령인, 연락처, 우편번호, 주소)를 모두 입력해주세요.');
      return;
    }
    if (!form.buyer.buyerName?.trim() || !form.buyer.buyerPhone?.trim()) {
      alert('주문자 이름과 연락처를 입력해주세요.');
      return;
    }

    setSubmitting(true);
    try {
      const orderRes = await createOrderFromCart({
        shipTo: form.shipTo,
        buyer: form.buyer,
        memo: form.memo || undefined,
      });
      const orderNo = orderRes?.orderNo;
      const amount = orderRes?.amount;
      if (!orderNo || amount == null) {
        throw new Error('주문 생성 응답이 올바르지 않습니다.');
      }

      const readyRes = await preparePayment(orderNo);
      const clientKey = readyRes?.clientKey ?? '';
      const customerKey = readyRes?.customerKey ?? `guest-${orderNo}`;
      const orderName = readyRes?.orderName ?? `주문 ${orderNo}`;
      const amountNumber = typeof amount === 'number' ? amount : Number(amount);
      const baseUrl = window.location.origin + '/shop';
      const successUrl = `${baseUrl}/payment/success`;
      const failUrl = `${baseUrl}/payment/fail`;
      const orderIdStr = typeof orderNo === 'string' ? orderNo : String(orderNo);

      const getTossPayments = await loadTossScript();
      const raw = getTossPayments ? getTossPayments(clientKey) : window.TossPayments?.(clientKey);
      const sdk = await Promise.resolve(raw);

      // 결제위젯 연동 키(문서용 테스트키 gck): sdk.widgets() 사용. docs.tosspayments.com/guides/v2/payment-widget/integration
      if (sdk?.widgets && customerKey) {
        const hadPrevInstance = !!widgetInstanceRef.current;
        let widgets = widgetInstanceRef.current;
        try {
          if (!widgets) {
            widgets = sdk.widgets({ customerKey });
          }
          await widgets.setAmount({ currency: 'KRW', value: amountNumber });
          if (!hadPrevInstance) {
            await widgets.renderPaymentMethods({ selector: '#toss-payment-method' });
            await widgets.renderAgreement({ selector: '#toss-agreement' });
          }
          widgetInstanceRef.current = widgets;
        } catch (err) {
          throw err;
        }
        setWidgetOrderPayload({
          orderId: orderIdStr,
          orderName,
          successUrl,
          failUrl,
          customerName: form.buyer?.buyerName || undefined,
          customerEmail: form.buyer?.buyerEmail || undefined,
          customerMobilePhone: form.buyer?.buyerPhone?.replace(/\D/g, '') || undefined,
        });
        setCheckoutPhase('widget_ready');
        setSubmitting(false);
        return;
      }

      // API 개별 연동 키(ck): sdk.payment().requestPayment() 사용
      const method = form.paymentMethod ?? 'CARD';
      if (sdk?.payment) {
        const payment = sdk.payment({ customerKey });
        await payment.requestPayment({
          method,
          amount: { value: amountNumber, currency: 'KRW' },
          orderId: orderIdStr,
          orderName,
          successUrl,
          failUrl,
          customerName: form.buyer?.buyerName || undefined,
          customerEmail: form.buyer?.buyerEmail || undefined,
          customerMobilePhone: form.buyer?.buyerPhone?.replace(/\D/g, '') || undefined,
        });
        return;
      }

      // v1 CDN: sdk.requestPayment(method, { amount, orderId, orderName, successUrl, failUrl })
      if (sdk?.requestPayment) {
        await sdk.requestPayment(method, {
          amount: amountNumber,
          orderId: orderNo,
          orderName,
          successUrl,
          failUrl,
        });
        return;
      }

      throw new Error('결제 SDK를 불러오지 못했습니다. (requestPayment 없음)');
    } catch (err) {
      console.error(err);
      alert(err?.message ?? '결제 준비 중 오류가 발생했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading && !cartSummary) {
    return (
      <div className="flex items-center justify-center min-h-[200px]">
        <p className="text-gray-500">장바구니 불러오는 중...</p>
      </div>
    );
  }

  const items = cartSummary?.items ?? cartItems ?? [];
  const totalPrice = cartSummary?.totals?.totalPrice ?? totals?.totalPrice ?? 0;
  const totalQty = cartSummary?.totals?.totalQty ?? items.reduce((s, i) => s + (i.qty ?? 0), 0);

  return (
    <div className="max-w-2xl mx-auto">
      <h1 className="text-2xl font-bold mb-6">주문/결제</h1>

      <section className="mb-8">
        <h2 className="text-lg font-semibold mb-3">주문 요약</h2>
        {items.length === 0 ? (
          <p className="text-gray-500">장바구니가 비어 있습니다. 상품을 담은 뒤 결제해주세요.</p>
        ) : (
          <div className="border rounded-lg p-4 bg-gray-50">
            <ul className="space-y-2 mb-3">
              {items.map((item) => (
                <li key={item.itemId} className="flex justify-between text-sm">
                  <span>{item.productName ?? '-'} x {(item.qty ?? 0)}</span>
                  <span>{(Number(item.price ?? 0) * (item.qty ?? 1)).toLocaleString()}원</span>
                </li>
              ))}
            </ul>
            <div className="flex justify-between font-semibold pt-2 border-t">
              <span>총 수량 {totalQty}개 / 결제 금액</span>
              <span>{Number(totalPrice).toLocaleString()}원</span>
            </div>
          </div>
        )}
      </section>

      <form onSubmit={handleSubmit} className="space-y-6">
        <section>
          <div className="flex items-center justify-between gap-2 mb-3">
            <h2 className="text-lg font-semibold">배송지</h2>
            <div className="relative">
              <button
                type="button"
                disabled={addressList.length === 0}
                onClick={() => setShowAddressSelect((v) => !v)}
                className="px-3 py-1.5 text-sm border rounded-lg hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                배송지 불러오기
              </button>
              {showAddressSelect && addressList.length > 0 && (
                <div className="absolute right-0 top-full mt-1 z-10 min-w-[240px] border rounded-lg bg-white shadow-lg py-1 max-h-48 overflow-auto">
                  {addressList.map((addr) => (
                    <button
                      key={addr.id}
                      type="button"
                      onClick={() => applyAddressToForm(addr)}
                      className="w-full text-left px-3 py-2 text-sm hover:bg-gray-100"
                    >
                      <span className="font-medium">{addr.shipToName}</span>
                      {addr.isDefault && (
                        <span className="ml-1 text-xs text-gray-500">(기본)</span>
                      )}
                      <br />
                      <span className="text-gray-600">
                        {[addr.shipAddress1, addr.shipAddress2].filter(Boolean).join(' ')}
                      </span>
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>
          <div className="space-y-3">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">수령인</label>
              <input
                type="text"
                required
                className="w-full border rounded-lg px-3 py-2"
                value={form.shipTo.recipientName}
                onChange={(e) => handleChange('shipTo', 'recipientName', e.target.value)}
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">연락처</label>
              <input
                type="tel"
                required
                className="w-full border rounded-lg px-3 py-2"
                value={form.shipTo.recipientPhone}
                onChange={(e) => handleChange('shipTo', 'recipientPhone', e.target.value)}
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">우편번호</label>
              <input
                type="text"
                required
                className="w-full border rounded-lg px-3 py-2"
                value={form.shipTo.zipcode}
                onChange={(e) => handleChange('shipTo', 'zipcode', e.target.value)}
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">주소</label>
              <input
                type="text"
                required
                className="w-full border rounded-lg px-3 py-2"
                value={form.shipTo.address1}
                onChange={(e) => handleChange('shipTo', 'address1', e.target.value)}
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">상세주소</label>
              <input
                type="text"
                className="w-full border rounded-lg px-3 py-2"
                value={form.shipTo.address2}
                onChange={(e) => handleChange('shipTo', 'address2', e.target.value)}
              />
            </div>
          </div>
        </section>

        <section>
          <h2 className="text-lg font-semibold mb-3">주문자 정보</h2>
          <div className="space-y-3">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">주문자 이름</label>
              <input
                type="text"
                required
                className="w-full border rounded-lg px-3 py-2"
                value={form.buyer.buyerName}
                onChange={(e) => handleChange('buyer', 'buyerName', e.target.value)}
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">이메일</label>
              <input
                type="email"
                className="w-full border rounded-lg px-3 py-2"
                value={form.buyer.buyerEmail}
                onChange={(e) => handleChange('buyer', 'buyerEmail', e.target.value)}
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">연락처</label>
              <input
                type="tel"
                required
                className="w-full border rounded-lg px-3 py-2"
                value={form.buyer.buyerPhone}
                onChange={(e) => handleChange('buyer', 'buyerPhone', e.target.value)}
              />
            </div>
          </div>
        </section>

        <section>
          <label className="block text-sm font-medium text-gray-700 mb-1">배송 메모</label>
          <input
            type="text"
            className="w-full border rounded-lg px-3 py-2"
            placeholder="선택"
            value={form.memo}
            onChange={(e) => setForm((p) => ({ ...p, memo: e.target.value }))}
          />
        </section>

        {/* 결제 수단: 결제위젯 연동 키(문서 테스트키) 시 위젯 렌더, API 개별 연동 키 시 라디오 선택 */}
        <section aria-label="결제 수단">
          <h2 className="text-lg font-semibold mb-3">결제 수단</h2>
          {checkoutPhase === 'widget_ready' && (
            <p className="text-sm text-gray-500 mb-2">토스 결제 위젯에서 결제 수단을 선택하고 약관에 동의한 뒤 아래 결제하기를 눌러주세요.</p>
          )}
          <div id="toss-payment-method" className="min-h-[80px]" />
          <div id="toss-agreement" className="min-h-[60px] mt-3" />
        </section>

        <div className="flex gap-3 pt-4">
          <button
            type="button"
            onClick={() => {
              if (checkoutPhase === 'widget_ready') {
                setCheckoutPhase('form');
                setWidgetOrderPayload(null);
              } else {
                navigate(-1);
              }
            }}
            className="px-4 py-2 border rounded-lg hover:bg-gray-50"
          >
            {checkoutPhase === 'widget_ready' ? '결제 수단 다시 선택' : '이전'}
          </button>
          <button
            type="submit"
            disabled={submitting || (checkoutPhase === 'form' && items.length === 0)}
            className="flex-1 py-3 bg-blue-500 text-white rounded-lg hover:bg-blue-600 disabled:opacity-50 disabled:cursor-not-allowed font-medium"
          >
            {submitting ? '처리 중...' : '결제하기'}
          </button>
        </div>
      </form>
    </div>
  );
};

export default CheckoutPage;
