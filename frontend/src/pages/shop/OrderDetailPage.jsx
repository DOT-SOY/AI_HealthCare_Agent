import { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
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
      <div className="max-w-2xl mx-auto text-center py-16">
        <p className="text-gray-600">주문 정보를 불러오는 중입니다...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="max-w-2xl mx-auto text-center py-16">
        <h1 className="text-xl font-bold text-red-600 mb-4">주문 조회 실패</h1>
        <p className="text-gray-700 mb-6">{error}</p>
        <button
          type="button"
          onClick={() => navigate(-1)}
          className="px-4 py-2 border rounded-lg hover:bg-gray-50"
        >
          이전 페이지로
        </button>
      </div>
    );
  }

  if (!order) {
    return (
      <div className="max-w-2xl mx-auto text-center py-16">
        <p className="text-gray-600">주문 정보를 찾을 수 없습니다.</p>
        <Link to="/shop/orders" className="text-blue-500 hover:underline">
          내 주문 내역
        </Link>
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
    <div className="max-w-3xl mx-auto">
      <h1 className="text-2xl font-bold mb-6">주문서</h1>

      <section className="mb-6 border rounded-lg bg-white p-4">
        <div className="flex flex-wrap justify-between gap-3 items-center mb-2">
          <div>
            <p className="text-sm text-gray-500">주문번호</p>
            <p className="font-semibold text-gray-900">{order.orderNo}</p>
          </div>
          <div className="text-right">
            <p className="text-sm text-gray-500">주문일시</p>
            <p className="font-semibold text-gray-900">
              {order.createdAt ? new Date(order.createdAt).toLocaleString() : '-'}
            </p>
          </div>
        </div>
        <div className="mt-2">
          <span className="inline-flex items-center px-3 py-1 rounded-full text-sm font-medium bg-blue-50 text-blue-700">
            {statusLabel}
          </span>
        </div>
      </section>

      <section className="mb-6 border rounded-lg bg-white p-4">
        <h2 className="text-lg font-semibold mb-3">주문자 정보</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
          <div>
            <p className="text-gray-500">이름</p>
            <p className="font-medium text-gray-900">{order.buyer?.name || '-'}</p>
          </div>
          <div>
            <p className="text-gray-500">연락처</p>
            <p className="font-medium text-gray-900">{order.buyer?.phone || '-'}</p>
          </div>
          <div className="sm:col-span-2">
            <p className="text-gray-500">이메일</p>
            <p className="font-medium text-gray-900">{order.buyer?.email || '-'}</p>
          </div>
        </div>
      </section>

      <section className="mb-6 border rounded-lg bg-white p-4">
        <div className="flex justify-between items-center mb-3">
          <h2 className="text-lg font-semibold">배송지 정보</h2>
          {canEditShipTo && !editingShipTo && (
            <button
              type="button"
              onClick={handleStartEditShipTo}
              className="px-3 py-1 text-sm border rounded-md hover:bg-gray-50"
            >
              배송지 수정
            </button>
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
              <label className="block text-gray-500 mb-1" htmlFor="recipientName">
                수령인
              </label>
              <input
                id="recipientName"
                name="recipientName"
                type="text"
                className="w-full border rounded px-2 py-1 text-sm"
                value={shipToForm.recipientName}
                onChange={handleShipToChange}
                disabled={savingShipTo}
                required
              />
            </div>
            <div>
              <label className="block text-gray-500 mb-1" htmlFor="recipientPhone">
                연락처
              </label>
              <input
                id="recipientPhone"
                name="recipientPhone"
                type="text"
                className="w-full border rounded px-2 py-1 text-sm"
                value={shipToForm.recipientPhone}
                onChange={handleShipToChange}
                disabled={savingShipTo}
                required
              />
            </div>
            <div>
              <label className="block text-gray-500 mb-1" htmlFor="zipcode">
                우편번호
              </label>
              <input
                id="zipcode"
                name="zipcode"
                type="text"
                className="w-full border rounded px-2 py-1 text-sm"
                value={shipToForm.zipcode}
                onChange={handleShipToChange}
                disabled={savingShipTo}
                required
              />
            </div>
            <div>
              <label className="block text-gray-500 mb-1" htmlFor="address1">
                주소
              </label>
              <input
                id="address1"
                name="address1"
                type="text"
                className="w-full border rounded px-2 py-1 text-sm"
                value={shipToForm.address1}
                onChange={handleShipToChange}
                disabled={savingShipTo}
                required
              />
            </div>
            <div className="sm:col-span-2">
              <label className="block text-gray-500 mb-1" htmlFor="address2">
                상세 주소
              </label>
              <input
                id="address2"
                name="address2"
                type="text"
                className="w-full border rounded px-2 py-1 text-sm"
                value={shipToForm.address2}
                onChange={handleShipToChange}
                disabled={savingShipTo}
              />
            </div>
            <div className="sm:col-span-2 flex justify-end gap-2 mt-2">
              <button
                type="button"
                onClick={handleCancelEditShipTo}
                className="px-3 py-1 text-sm border rounded-md hover:bg-gray-50"
                disabled={savingShipTo}
              >
                취소
              </button>
              <button
                type="submit"
                className="px-3 py-1 text-sm rounded-md bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-60"
                disabled={savingShipTo}
              >
                {savingShipTo ? '저장 중...' : '저장'}
              </button>
            </div>
          </form>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
            <div>
              <p className="text-gray-500">수령인</p>
              <p className="font-medium text-gray-900">{order.shipTo?.recipientName || '-'}</p>
            </div>
            <div>
              <p className="text-gray-500">연락처</p>
              <p className="font-medium text-gray-900">{order.shipTo?.recipientPhone || '-'}</p>
            </div>
            <div className="sm:col-span-2">
              <p className="text-gray-500">주소</p>
              <p className="font-medium text-gray-900">
                {order.shipTo?.zipcode ? `[${order.shipTo.zipcode}] ` : ''}
                {order.shipTo?.address1 || ''}
                {order.shipTo?.address2 ? `, ${order.shipTo.address2}` : ''}
              </p>
            </div>
            {!canEditShipTo && (
              <div className="sm:col-span-2 text-xs text-gray-500">
                발송 이후에는 배송지 수정이 불가능합니다.
              </div>
            )}
          </div>
        )}
      </section>

      <section className="mb-6 border rounded-lg bg-white p-4">
        <h2 className="text-lg font-semibold mb-3">상품 정보</h2>
        {order.items && order.items.length > 0 ? (
          <div className="space-y-3">
            {order.items.map((item) => (
              <div key={item.id} className="flex justify-between gap-3 text-sm border-b pb-2 last:border-b-0">
                <div className="flex-1">
                  <p className="font-medium text-gray-900">{item.productName}</p>
                  {item.variantOption && (
                    <p className="text-gray-500 mt-0.5 text-xs">옵션: {item.variantOption}</p>
                  )}
                  <p className="text-gray-600 mt-1 text-xs">수량: {item.qty}개</p>
                </div>
                <div className="text-right whitespace-nowrap">
                  <p className="text-gray-500 text-xs">
                    단가 {Number(item.unitPrice ?? 0).toLocaleString()}원
                  </p>
                  <p className="font-semibold text-gray-900 mt-1">
                    {Number(item.lineAmount ?? 0).toLocaleString()}원
                  </p>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-sm text-gray-500">주문 상품이 없습니다.</p>
        )}
      </section>

      <section className="mb-8 border rounded-lg bg-white p-4">
        <h2 className="text-lg font-semibold mb-3">결제 금액</h2>
        <div className="space-y-2 text-sm">
          <div className="flex justify-between">
            <span className="text-gray-600">상품 합계</span>
            <span className="font-medium text-gray-900">
              {Number(order.totalItemAmount ?? 0).toLocaleString()}원
            </span>
          </div>
          <div className="flex justify-between">
            <span className="text-gray-600">배송비</span>
            <span className="font-medium text-gray-900">
              {Number(order.shippingFee ?? 0).toLocaleString()}원
            </span>
          </div>
          <div className="flex justify-between border-t pt-2 mt-2 text-base">
            <span className="font-semibold">총 결제 금액</span>
            <span className="font-bold text-blue-600">
              {Number(order.totalPayableAmount ?? 0).toLocaleString()}원
            </span>
          </div>
        </div>
      </section>

      <div className="flex gap-3">
        <button
          type="button"
          onClick={() => navigate('/shop/orders')}
          className="px-4 py-2 border rounded-lg hover:bg-gray-50"
        >
          내 주문 내역
        </button>
      </div>
    </div>
  );
};

export default OrderDetailPage;

