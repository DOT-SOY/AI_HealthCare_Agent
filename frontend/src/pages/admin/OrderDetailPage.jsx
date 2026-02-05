import { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useSelector } from 'react-redux';
import Button from '../../components/common/Button';
import Card from '../../components/common/Card';
import { getAdminOrderDetail, updateOrderStatusAdmin } from '../../services/orderApi';

const statusLabels = {
  CREATED: '주문 생성',
  PAYMENT_PENDING: '결제 대기',
  PAID: '결제 완료',
  SHIPPED: '배송중',
  DELIVERED: '배송완료',
  CANCELED: '취소',
};

const nextStatusOptions = {
  PAID: [
    { value: 'SHIPPED', label: '배송중' },
    { value: 'CANCELED', label: '취소' },
  ],
  SHIPPED: [
    { value: 'DELIVERED', label: '배송완료' },
    { value: 'CANCELED', label: '취소' },
  ],
  CREATED: [{ value: 'CANCELED', label: '취소' }],
  PAYMENT_PENDING: [{ value: 'CANCELED', label: '취소' }],
};

const OrderDetailPage = () => {
  const { orderNo } = useParams();
  const navigate = useNavigate();
  const loginState = useSelector((state) => state.loginSlice);
  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selectedStatus, setSelectedStatus] = useState('');
  const [savingStatus, setSavingStatus] = useState(false);

  useEffect(() => {
    if (!loginState.roleNames || !loginState.roleNames.includes('ADMIN')) {
      navigate('/', { replace: true });
      return;
    }
  }, [loginState, navigate]);

  useEffect(() => {
    let cancelled = false;
    const run = async () => {
      try {
        setLoading(true);
        setError('');
        const res = await getAdminOrderDetail(orderNo);
        if (cancelled) return;
        setOrder(res);
        setSelectedStatus('');
      } catch (err) {
        if (cancelled) return;
        setError(err?.message || '주문 정보를 불러오는데 실패했습니다.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    if (orderNo && loginState.roleNames?.includes('ADMIN')) {
      run();
    } else if (!orderNo) {
      setLoading(false);
      setError('유효하지 않은 주문번호입니다.');
    }
    return () => { cancelled = true; };
  }, [orderNo, loginState.roleNames]);

  const handleStatusChange = async () => {
    if (!selectedStatus || !order) return;
    try {
      setSavingStatus(true);
      setError('');
      await updateOrderStatusAdmin(order.orderNo, selectedStatus);
      setOrder((prev) => ({ ...prev, status: selectedStatus }));
      setSelectedStatus('');
    } catch (err) {
      setError(err?.message || '배송 상태 변경에 실패했습니다.');
    } finally {
      setSavingStatus(false);
    }
  };

  if (!loginState.roleNames?.includes('ADMIN')) {
    return null;
  }

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-token-4">
        <div className="spinner-token" />
        <p className="text-text-sub font-medium">주문 정보를 불러오는 중입니다...</p>
      </div>
    );
  }

  if (error && !order) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[40vh] gap-token-4 text-center">
        <h1 className="text-xl font-emphasis text-accent-secondary">주문 조회 실패</h1>
        <p className="text-text-sub max-w-md">{error}</p>
        <Button type="button" variant="ghost" size="md" onClick={() => navigate(-1)}>
          이전 페이지로
        </Button>
      </div>
    );
  }

  if (!order) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[40vh] gap-token-4 text-center">
        <p className="text-text-sub">주문 정보를 찾을 수 없습니다.</p>
        <Button as={Link} to="/admin/orders" variant="ghost" size="sm">
          주문 목록
        </Button>
      </div>
    );
  }

  const statusLabel = statusLabels[order.status] || order.status;
  const options = nextStatusOptions[order.status] || [];

  return (
    <div className="space-y-token-6">
      <header className="section-header-token">
        <h1 className="section-title">주문 상세 (관리자)</h1>
        <p className="section-desc">주문 내역을 확인하고 배송 상태를 변경하세요.</p>
      </header>

      {error && (
        <div className="rounded-token border border-accent-secondary/50 bg-accent-secondary/5 px-4 py-2 text-sm text-accent-secondary">
          {error}
        </div>
      )}

      <section>
        <Card className="p-token-4">
          <div className="flex flex-wrap justify-between gap-3 items-center mb-2">
            <div>
              <p className="text-xs text-text-muted">주문번호</p>
              <p className="font-medium text-text-main">{order.orderNo}</p>
            </div>
            <div className="text-right">
              <p className="text-xs text-text-muted">주문일시</p>
              <p className="font-medium text-text-main">
                {order.createdAt ? new Date(order.createdAt).toLocaleString() : '-'}
              </p>
            </div>
          </div>
          <div className="mt-2">
            <span className="inline-flex items-center rounded-full border border-primary-500/60 px-3 py-0.5 text-xs font-medium text-primary-500 bg-primary-500/5">
              {statusLabel}
            </span>
          </div>
        </Card>
      </section>

      {/* 관리자: 배송 상태 변경 */}
      {options.length > 0 && (
        <Card className="p-token-4">
          <h2 className="text-base font-semibold mb-3 text-text-main">배송 상태 변경</h2>
          <div className="flex flex-wrap gap-3 items-center">
            <select
              value={selectedStatus}
              onChange={(e) => setSelectedStatus(e.target.value)}
              className="input-token min-w-[160px]"
              disabled={savingStatus}
            >
              <option value="">선택</option>
              {options.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
            <Button
              type="button"
              variant="primary"
              size="sm"
              onClick={handleStatusChange}
              disabled={!selectedStatus || savingStatus}
            >
              {savingStatus ? '저장 중...' : '변경 적용'}
            </Button>
          </div>
        </Card>
      )}

      <section className="grid lg:grid-cols-2 gap-token-4">
        <Card className="p-token-4">
          <h2 className="text-base font-semibold mb-3 text-text-main">주문자 정보</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
            <div>
              <p className="text-text-muted text-xs">이름</p>
              <p className="font-medium text-text-main">{order.buyer?.name || '-'}</p>
            </div>
            <div>
              <p className="text-text-muted text-xs">연락처</p>
              <p className="font-medium text-text-main">{order.buyer?.phone || '-'}</p>
            </div>
            <div className="sm:col-span-2">
              <p className="text-text-muted text-xs">이메일</p>
              <p className="font-medium text-text-main break-all">{order.buyer?.email || '-'}</p>
            </div>
          </div>
        </Card>

        <Card className="p-token-4">
          <h2 className="text-base font-semibold mb-3 text-text-main">배송지 정보</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
            <div>
              <p className="text-text-muted text-xs">수령인</p>
              <p className="font-medium text-text-main">{order.shipTo?.recipientName || '-'}</p>
            </div>
            <div>
              <p className="text-text-muted text-xs">연락처</p>
              <p className="font-medium text-text-main">{order.shipTo?.recipientPhone || '-'}</p>
            </div>
            <div className="sm:col-span-2">
              <p className="text-text-muted text-xs">주소</p>
              <p className="font-medium text-text-main">
                {order.shipTo?.zipcode ? `[${order.shipTo.zipcode}] ` : ''}
                {order.shipTo?.address1 || ''}
                {order.shipTo?.address2 ? `, ${order.shipTo.address2}` : ''}
              </p>
            </div>
          </div>
        </Card>
      </section>

      <section className="grid lg:grid-cols-3 gap-token-4">
        <Card className="p-token-4 lg:col-span-2">
          <h2 className="text-base font-semibold mb-3 text-text-main">상품 정보</h2>
          {order.items && order.items.length > 0 ? (
            <div className="space-y-3">
              {order.items.map((item) => (
                <div
                  key={item.id}
                  className="flex justify-between gap-3 text-sm border-b border-border-default pb-2 last:border-b-0"
                >
                  <div className="flex-1">
                    <p className="font-medium text-text-main">{item.productName}</p>
                    {item.variantOption && (
                      <p className="text-text-muted mt-0.5 text-xs">옵션: {item.variantOption}</p>
                    )}
                    <p className="text-text-muted mt-1 text-xs">수량: {item.qty}개</p>
                  </div>
                  <div className="text-right whitespace-nowrap">
                    <p className="text-text-muted text-xs">
                      단가 {Number(item.unitPrice ?? 0).toLocaleString()}원
                    </p>
                    <p className="font-medium text-text-main mt-1">
                      {Number(item.lineAmount ?? 0).toLocaleString()}원
                    </p>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-sm text-text-muted">주문 상품이 없습니다.</p>
          )}
        </Card>

        <Card className="p-token-4">
          <h2 className="text-base font-semibold mb-3 text-text-main">결제 금액</h2>
          <div className="space-y-2 text-sm">
            <div className="flex justify-between">
              <span className="text-text-muted">상품 합계</span>
              <span className="font-medium text-text-main">
                {Number(order.totalItemAmount ?? 0).toLocaleString()}원
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-text-muted">배송비</span>
              <span className="font-medium text-text-main">
                {Number(order.shippingFee ?? 0).toLocaleString()}원
              </span>
            </div>
            <div className="flex justify-between border-t border-border-default pt-2 mt-2 text-base">
              <span className="font-semibold text-text-main">총 결제 금액</span>
              <span className="font-emphasis text-primary-500">
                {Number(order.totalPayableAmount ?? 0).toLocaleString()}원
              </span>
            </div>
          </div>
        </Card>
      </section>

      <div className="flex gap-3">
        <Button as={Link} to="/admin/orders" variant="ghost" size="sm">
          주문 목록
        </Button>
      </div>
    </div>
  );
};

export default OrderDetailPage;
