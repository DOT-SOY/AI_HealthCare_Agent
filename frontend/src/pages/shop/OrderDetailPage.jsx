import { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import Button from '../../components/common/Button';
import Card from '../../components/common/Card';
import { getOrderDetail, updateOrderShipTo } from '../../services/orderApi';

const statusLabels = {
  CREATED: '주문 생성',
  PAYMENT_PENDING: '결제 대기',
  PAID: '결제 완료',
  SHIPPED: '배송중',
  DELIVERED: '배송완료',
  CANCELED: '취소',
};

const OrderDetailPage = () => {
  const { orderNo } = useParams();
  const navigate = useNavigate();
  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [editingShipTo, setEditingShipTo] = useState(false);
  const [shipToForm, setShipToForm] = useState(null);
  const [savingShipTo, setSavingShipTo] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const run = async () => {
      try {
        setLoading(true);
        setError('');
        const res = await getOrderDetail(orderNo);
        if (cancelled) return;
        setOrder(res);
        setShipToForm({
          recipientName: res.shipTo?.recipientName || '',
          recipientPhone: res.shipTo?.recipientPhone || '',
          zipcode: res.shipTo?.zipcode || '',
          address1: res.shipTo?.address1 || '',
          address2: res.shipTo?.address2 || '',
        });
      } catch (err) {
        if (cancelled) return;
        setError(err?.message || '주문 정보를 불러오는데 실패했습니다.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    if (orderNo) {
      run();
    } else {
      setLoading(false);
      setError('유효하지 않은 주문번호입니다.');
    }
    return () => {
      cancelled = true;
    };
  }, [orderNo]);

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-token-4">
        <div className="spinner-token" />
        <p className="text-text-sub font-medium">주문 정보를 불러오는 중입니다...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[40vh] gap-token-4 text-center">
        <h1 className="text-xl font-emphasis text-accent-secondary">주문 조회 실패</h1>
        <p className="text-text-sub max-w-md">{error}</p>
        <Button
          type="button"
          variant="ghost"
          size="md"
          onClick={() => navigate(-1)}
        >
          이전 페이지로
        </Button>
      </div>
    );
  }

  if (!order) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[40vh] gap-token-4 text-center">
        <p className="text-text-sub">주문 정보를 찾을 수 없습니다.</p>
        <Button as={Link} to="/shop/orders" variant="ghost" size="sm">
          내 주문 내역
        </Button>
      </div>
    );
  }

  const statusLabel = statusLabels[order.status] || order.status;
  const canEditShipTo =
    order &&
    (order.status === 'CREATED' ||
      order.status === 'PAYMENT_PENDING' ||
      order.status === 'PAID');

  const handleShipToChange = (e) => {
    const { name, value } = e.target;
    setShipToForm((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  const resetShipToFormFromOrder = () => {
    setShipToForm({
      recipientName: order.shipTo?.recipientName || '',
      recipientPhone: order.shipTo?.recipientPhone || '',
      zipcode: order.shipTo?.zipcode || '',
      address1: order.shipTo?.address1 || '',
      address2: order.shipTo?.address2 || '',
    });
  };

  const handleStartEditShipTo = () => {
    resetShipToFormFromOrder();
    setEditingShipTo(true);
  };

  const handleCancelEditShipTo = () => {
    resetShipToFormFromOrder();
    setEditingShipTo(false);
  };

  const handleSaveShipTo = async () => {
    if (!shipToForm) return;
    try {
      setSavingShipTo(true);
      setError('');
      await updateOrderShipTo(order.orderNo, shipToForm);
      setOrder((prev) => ({
        ...prev,
        shipTo: {
          ...prev.shipTo,
          ...shipToForm,
        },
      }));
      setEditingShipTo(false);
    } catch (err) {
      setError(err?.message || '배송지 정보를 저장하는 데 실패했습니다.');
    } finally {
      setSavingShipTo(false);
    }
  };

  return (
    <div className="space-y-token-6">
      <header className="section-header-token">
        <h1 className="section-title">주문서</h1>
        <p className="section-desc">주문 내역과 배송지 정보를 확인하고 관리하세요.</p>
      </header>

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
          <div className="flex justify-between items-center mb-3 gap-2">
            <h2 className="text-base font-semibold text-text-main">배송지 정보</h2>
            {canEditShipTo && !editingShipTo && (
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={handleStartEditShipTo}
              >
                배송지 수정
              </Button>
            )}
          </div>

          {editingShipTo && shipToForm ? (
            <form
              className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm"
              onSubmit={(e) => {
                e.preventDefault();
                handleSaveShipTo();
              }}
            >
              <div>
                <label className="block text-xs text-text-muted mb-1" htmlFor="recipientName">
                  수령인
                </label>
                <input
                  id="recipientName"
                  name="recipientName"
                  type="text"
                  className="input-token w-full"
                  value={shipToForm.recipientName}
                  onChange={handleShipToChange}
                  disabled={savingShipTo}
                  required
                />
              </div>
              <div>
                <label className="block text-xs text-text-muted mb-1" htmlFor="recipientPhone">
                  연락처
                </label>
                <input
                  id="recipientPhone"
                  name="recipientPhone"
                  type="text"
                  className="input-token w-full"
                  value={shipToForm.recipientPhone}
                  onChange={handleShipToChange}
                  disabled={savingShipTo}
                  required
                />
              </div>
              <div>
                <label className="block text-xs text-text-muted mb-1" htmlFor="zipcode">
                  우편번호
                </label>
                <input
                  id="zipcode"
                  name="zipcode"
                  type="text"
                  className="input-token w-full"
                  value={shipToForm.zipcode}
                  onChange={handleShipToChange}
                  disabled={savingShipTo}
                  required
                />
              </div>
              <div>
                <label className="block text-xs text-text-muted mb-1" htmlFor="address1">
                  주소
                </label>
                <input
                  id="address1"
                  name="address1"
                  type="text"
                  className="input-token w-full"
                  value={shipToForm.address1}
                  onChange={handleShipToChange}
                  disabled={savingShipTo}
                  required
                />
              </div>
              <div className="sm:col-span-2">
                <label className="block text-xs text-text-muted mb-1" htmlFor="address2">
                  상세 주소
                </label>
                <input
                  id="address2"
                  name="address2"
                  type="text"
                  className="input-token w-full"
                  value={shipToForm.address2}
                  onChange={handleShipToChange}
                  disabled={savingShipTo}
                />
              </div>
              <div className="sm:col-span-2 flex justify-end gap-2 mt-2">
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={handleCancelEditShipTo}
                  disabled={savingShipTo}
                >
                  취소
                </Button>
                <Button
                  type="submit"
                  size="sm"
                  disabled={savingShipTo}
                >
                  {savingShipTo ? '저장 중...' : '저장'}
                </Button>
              </div>
            </form>
          ) : (
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
              {!canEditShipTo && (
                <div className="sm:col-span-2 text-xs text-text-muted">
                  발송 이후에는 배송지 수정이 불가능합니다.
                </div>
              )}
            </div>
          )}
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
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={() => navigate('/shop/orders')}
        >
          내 주문 내역
        </Button>
      </div>
    </div>
  );
};

export default OrderDetailPage;

