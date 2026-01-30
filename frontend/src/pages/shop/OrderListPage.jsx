import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { getMyOrders } from '../../services/orderApi';

const statusLabels = {
  CREATED: '주문 생성',
  PAYMENT_PENDING: '결제 대기',
  PAID: '결제 완료',
  SHIPPED: '배송중',
  DELIVERED: '배송완료',
  CANCELED: '취소',
};

const statusOptions = [
  { value: '', label: '전체' },
  { value: 'PAID', label: '결제 완료' },
  { value: 'SHIPPED', label: '배송중' },
  { value: 'DELIVERED', label: '배송완료' },
  { value: 'CANCELED', label: '취소' },
];

const OrderListPage = () => {
  const navigate = useNavigate();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [page, setPage] = useState(1);
  const [pageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [hasPrevious, setHasPrevious] = useState(false);
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [status, setStatus] = useState('');

  useEffect(() => {
    let cancelled = false;
    const run = async () => {
      try {
        setLoading(true);
        setError('');
        const params = {
          page,
          page_size: pageSize,
          ...(fromDate && { from_date: fromDate }),
          ...(toDate && { to_date: toDate }),
          ...(status && { status }),
        };
        const res = await getMyOrders(params);
        if (cancelled) return;
        setItems(res.items || []);
        setTotal(res.total ?? 0);
        setHasNext(res.has_next ?? false);
        setHasPrevious(res.has_previous ?? false);
      } catch (err) {
        if (cancelled) return;
        const msg = err?.message || '주문 내역을 불러오는데 실패했습니다.';
        if (err?.message?.includes('401') || msg.toLowerCase().includes('unauthorized')) {
          navigate('/member/login', { state: { from: '/shop/orders' }, replace: true });
          return;
        }
        setError(msg);
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    run();
    return () => { cancelled = true; };
  }, [page, fromDate, toDate, status, navigate]);

  const handleApplyFilter = (e) => {
    e?.preventDefault?.();
    setPage(1);
  };

  const handlePageChange = (newPage) => {
    setPage(newPage);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  if (loading && items.length === 0) {
    return (
      <div className="max-w-3xl mx-auto text-center py-16">
        <p className="text-gray-600">주문 내역을 불러오는 중입니다...</p>
      </div>
    );
  }

  if (error && items.length === 0) {
    return (
      <div className="max-w-3xl mx-auto text-center py-16">
        <h1 className="text-xl font-bold text-red-600 mb-4">주문 내역 조회 실패</h1>
        <p className="text-gray-700 mb-6">{error}</p>
        <button
          type="button"
          onClick={() => navigate('/member/login', { state: { from: '/shop/orders' } })}
          className="px-4 py-2 border rounded-lg hover:bg-gray-50"
        >
          로그인하기
        </button>
      </div>
    );
  }

  return (
    <div className="max-w-3xl mx-auto">
      <h1 className="text-2xl font-bold mb-6">내 주문 내역</h1>

      <section className="mb-6 border rounded-lg bg-white p-4">
        <form onSubmit={handleApplyFilter} className="flex flex-wrap gap-3 items-end">
          <div>
            <label className="block text-sm text-gray-500 mb-1">시작일</label>
            <input
              type="date"
              value={fromDate}
              onChange={(e) => setFromDate(e.target.value)}
              className="px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label className="block text-sm text-gray-500 mb-1">종료일</label>
            <input
              type="date"
              value={toDate}
              onChange={(e) => setToDate(e.target.value)}
              className="px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label className="block text-sm text-gray-500 mb-1">상태</label>
            <select
              value={status}
              onChange={(e) => setStatus(e.target.value)}
              className="px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              {statusOptions.map((opt) => (
                <option key={opt.value || 'all'} value={opt.value}>{opt.label}</option>
              ))}
            </select>
          </div>
          <button
            type="submit"
            className="px-4 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600"
          >
            검색
          </button>
          <button
            type="button"
            onClick={() => {
              setFromDate('');
              setToDate('');
              setStatus('');
              setPage(1);
            }}
            className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50"
          >
            검색 초기화
          </button>
        </form>
      </section>

      {items.length === 0 ? (
        <div className="border rounded-lg bg-white p-8 text-center text-gray-500">
          <p>주문 내역이 없습니다.</p>
        </div>
      ) : (
        <>
          <ul className="space-y-3">
            {items.map((order) => (
              <li key={order.orderNo}>
                <Link
                  to={`/shop/orders/${encodeURIComponent(order.orderNo)}`}
                  className="block border rounded-lg bg-white p-4 hover:bg-gray-50 transition"
                >
                  <div className="flex flex-wrap justify-between gap-3 items-center">
                    <div>
                      <p className="font-semibold text-gray-900">
                        {order.firstProductName || '상품'}
                        {(order.itemCount ?? 0) > 1 && (
                          <span className="text-gray-500 font-normal"> 외 {(order.itemCount ?? 0) - 1}개</span>
                        )}
                      </p>
                      <p className="text-sm text-gray-500 mt-0.5">
                        {order.createdAt ? new Date(order.createdAt).toLocaleString() : '-'}
                      </p>
                    </div>
                    <div className="text-right">
                      <span className="inline-flex items-center px-2.5 py-0.5 rounded text-xs font-medium bg-blue-50 text-blue-700 mr-2">
                        {statusLabels[order.status] ?? order.status}
                      </span>
                      <p className="font-semibold text-gray-900 mt-1">
                        {Number(order.totalPayableAmount ?? 0).toLocaleString()}원
                      </p>
                    </div>
                  </div>
                </Link>
              </li>
            ))}
          </ul>

          <div className="mt-6 flex justify-center gap-2 items-center">
            <button
              type="button"
              onClick={() => handlePageChange(page - 1)}
              disabled={!hasPrevious}
              className="px-4 py-2 border rounded-lg hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              이전
            </button>
            <span className="text-sm text-gray-600">
              {page} / {total > 0 ? Math.ceil(total / pageSize) : 1} (총 {total}건)
            </span>
            <button
              type="button"
              onClick={() => handlePageChange(page + 1)}
              disabled={!hasNext}
              className="px-4 py-2 border rounded-lg hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              다음
            </button>
          </div>
        </>
      )}

      <div className="mt-6">
        <Link to="/shop/list" className="text-blue-500 hover:underline">
          쇼핑하러 가기
        </Link>
      </div>
    </div>
  );
};

export default OrderListPage;
