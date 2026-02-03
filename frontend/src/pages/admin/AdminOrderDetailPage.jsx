import { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { getAdminOrderDetail, updateOrderStatusAdmin } from '../../services/orderApi';

const statusLabels = {
  CREATED: '주문 생성',
  PAYMENT_PENDING: '결제 대기',
  PAID: '결제 완료',
  SHIPPED: '배송중',
  DELIVERED: '배송완료',
  CANCELED: '취소',
};

/** PAID → SHIPPED, SHIPPED → DELIVERED, PAID/SHIPPED 등 → CANCELED, CANCELED → PAID(복구). 취소 상태에서도 복구 등 변경 가능 */
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
  CANCELED: [{ value: 'PAID', label: '결제 완료 (복구)' }],
  DELIVERED: [], // 배송완료 후에는 변경 불가
};

const AdminOrderDetailPage = () => {
  const { orderNo } = useParams();
  const navigate = useNavigate();
  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [updatingStatus, setUpdatingStatus] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const run = async () => {
      if (!orderNo) {
        setLoading(false);
        setError('유효하지 않은 주문번호입니다.');
        return;
      }
      try {
        setLoading(true);
        setError('');
        const res = await getAdminOrderDetail(orderNo);
        if (cancelled) return;
        setOrder(res);
      } catch (err) {
        if (cancelled) return;
        setError(err?.message || '주문 정보를 불러오는데 실패했습니다.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    run();
    return () => { cancelled = true; };
  }, [orderNo]);

  const handleStatusChange = async (newStatus) => {
    if (!orderNo || !newStatus) return;
    try {
      setUpdatingStatus(true);
      await updateOrderStatusAdmin(orderNo, newStatus);
      setOrder((prev) => (prev ? { ...prev, status: newStatus } : null));
    } catch (err) {
      alert(err?.message || '상태 변경에 실패했습니다.');
    } finally {
      setUpdatingStatus(false);
    }
  };

  if (loading) {
    return (
      <div className="max-w-3xl mx-auto text-center py-16">
        <p className="text-gray-600">주문 정보를 불러오는 중입니다...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="max-w-3xl mx-auto text-center py-16">
        <h1 className="text-xl font-bold text-red-600 mb-4">주문 조회 실패</h1>
        <p className="text-gray-700 mb-6">{error}</p>
        <button
          type="button"
          onClick={() => navigate('/admin/orders')}
          className="px-4 py-2 border rounded-lg hover:bg-gray-50"
        >
          주문 목록으로
        </button>
      </div>
    );
  }

  if (!order) {
    return (
      <div className="max-w-3xl mx-auto text-center py-16">
        <p className="text-gray-600">주문 정보를 찾을 수 없습니다.</p>
        <Link to="/admin/orders" className="text-blue-500 hover:underline">
          주문 목록
        </Link>
      </div>
    );
  }

  const statusKey = (order.status && String(order.status).toUpperCase()) || '';
  const statusLabel = statusLabels[order.status] ?? statusLabels[statusKey] ?? order.status;
  const options = nextStatusOptions[statusKey] ?? nextStatusOptions[order.status] ?? [];

  return (
    <div className="max-w-3xl mx-auto">
      <div className="flex flex-wrap justify-between items-center gap-3 mb-6">
        <h1 className="text-2xl font-bold">주문 배송 상태 관리</h1>
        <Link
          to="/admin/orders"
          className="px-4 py-2 border rounded-lg hover:bg-gray-50 text-gray-700"
        >
          ← 주문 목록
        </Link>
      </div>

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
        <div className="mt-3 flex flex-wrap items-center gap-3">
          <span className="inline-flex items-center px-3 py-1 rounded-full text-sm font-medium bg-blue-50 text-blue-700">
            {statusLabel}
          </span>
          {options.length > 0 && (
            <div className="flex items-center gap-2">
              <span className="text-sm text-gray-500">배송 상태 변경:</span>
              <select
                value=""
                onChange={(e) => {
                  const v = e.target.value;
                  if (v) handleStatusChange(v);
                }}
                disabled={updatingStatus}
                className="px-3 py-1.5 border rounded-lg text-sm disabled:opacity-50 focus:ring-2 focus:ring-blue-500"
              >
                <option value="">선택</option>
                {options.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </div>
          )}
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
        <h2 className="text-lg font-semibold mb-3">배송지 정보</h2>
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
        </div>
      </section>

      <section className="mb-6 border rounded-lg bg-white p-4">
        <h2 className="text-lg font-semibold mb-3">상품 정보</h2>
        {order.items && order.items.length > 0 ? (
          <div className="space-y-3">
            {order.items.map((item) => (
              <div
                key={item.id}
                className="flex justify-between gap-3 text-sm border-b pb-2 last:border-b-0"
              >
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
        <Link
          to="/admin/orders"
          className="px-4 py-2 border rounded-lg hover:bg-gray-50"
        >
          주문 목록으로
        </Link>
      </div>
    </div>
  );
};

export default AdminOrderDetailPage;
