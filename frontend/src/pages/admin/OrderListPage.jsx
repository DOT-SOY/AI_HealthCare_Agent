import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useSelector } from 'react-redux';
import Button from '../../components/common/Button';
import Card from '../../components/common/Card';
import { getAdminOrders } from '../../services/orderApi';

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
  const loginState = useSelector((state) => state.loginSlice);
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
    if (!loginState.roleNames || !loginState.roleNames.includes('ADMIN')) {
      navigate('/', { replace: true });
      return;
    }
  }, [loginState, navigate]);

  useEffect(() => {
    if (!loginState.roleNames?.includes('ADMIN')) return;
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
        const res = await getAdminOrders(params);
        if (cancelled) return;
        setItems(res.items || []);
        setTotal(res.total ?? 0);
        setHasNext(res.has_next ?? false);
        setHasPrevious(res.has_previous ?? false);
      } catch (err) {
        if (cancelled) return;
        const msg = err?.message || '주문 목록을 불러오는데 실패했습니다.';
        if (msg.toLowerCase().includes('unauthorized') || msg.includes('403')) {
          navigate('/', { replace: true });
          return;
        }
        setError(msg);
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    run();
    return () => { cancelled = true; };
  }, [page, fromDate, toDate, status, loginState.roleNames, navigate]);

  const handleApplyFilter = (e) => {
    e?.preventDefault?.();
    setPage(1);
  };

  const handlePageChange = (newPage) => {
    setPage(newPage);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  if (!loginState.roleNames?.includes('ADMIN')) {
    return null;
  }

  if (loading && items.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-token-4">
        <div className="spinner-token" />
        <p className="text-text-sub font-medium">주문 목록을 불러오는 중입니다...</p>
      </div>
    );
  }

  if (error && items.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[40vh] gap-token-4 text-center">
        <h1 className="text-xl font-emphasis text-accent-secondary">주문 목록 조회 실패</h1>
        <p className="text-text-sub max-w-md">{error}</p>
      </div>
    );
  }

  const totalPages = total > 0 ? Math.ceil(total / pageSize) : 1;

  return (
    <div className="space-y-token-6">
      <header className="section-header-token">
        <h1 className="section-title">전체 주문 내역</h1>
        <p className="section-desc">모든 회원의 주문 현황을 확인하고, 주문을 클릭해 배송 상태를 변경하세요.</p>
      </header>

      <section>
        <Card className="p-token-4">
          <form onSubmit={handleApplyFilter} className="flex flex-wrap gap-4 items-end">
            <div className="flex flex-col gap-1">
              <label className="text-xs font-medium text-text-muted">시작일</label>
              <input
                type="date"
                value={fromDate}
                onChange={(e) => setFromDate(e.target.value)}
                className="input-token min-w-[180px]"
              />
            </div>
            <div className="flex flex-col gap-1">
              <label className="text-xs font-medium text-text-muted">종료일</label>
              <input
                type="date"
                value={toDate}
                onChange={(e) => setToDate(e.target.value)}
                className="input-token min-w-[180px]"
              />
            </div>
            <div className="flex flex-col gap-1">
              <label className="text-xs font-medium text-text-muted">상태</label>
              <select
                value={status}
                onChange={(e) => setStatus(e.target.value)}
                className="input-token min-w-[140px]"
              >
                {statusOptions.map((opt) => (
                  <option key={opt.value || 'all'} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </div>
            <div className="flex gap-2 ml-auto">
              <Button type="submit" variant="primary" size="sm">
                검색
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => {
                  setFromDate('');
                  setToDate('');
                  setStatus('');
                  setPage(1);
                }}
              >
                초기화
              </Button>
            </div>
          </form>
        </Card>
      </section>

      {items.length === 0 ? (
        <Card className="p-token-6 text-center">
          <p className="text-text-muted">주문 내역이 없습니다.</p>
        </Card>
      ) : (
        <>
          <ul className="space-y-3">
            {items.map((order) => (
              <li key={order.orderNo}>
                <Card
                  as={Link}
                  to={`/admin/orders/${encodeURIComponent(order.orderNo)}`}
                  className="p-token-4 hover:shadow-card-hover hover:border-primary-500 transition-colors flex flex-wrap justify-between gap-3 items-center"
                >
                  <div>
                    <p className="font-medium text-text-main">
                      {order.firstProductName || '상품'}
                      {(order.itemCount ?? 0) > 1 && (
                        <span className="text-text-muted font-normal">
                          {' '}
                          외 {(order.itemCount ?? 0) - 1}개
                        </span>
                      )}
                    </p>
                    <p className="text-xs text-text-muted mt-1">
                      {order.createdAt ? new Date(order.createdAt).toLocaleString() : '-'}
                    </p>
                  </div>
                  <div className="text-right space-y-1">
                    <span className="inline-flex items-center rounded-full border border-primary-500/60 px-3 py-0.5 text-xs font-medium text-primary-500 bg-primary-500/5">
                      {statusLabels[order.status] ?? order.status}
                    </span>
                    <p className="font-emphasis text-primary-500 text-base">
                      {Number(order.totalPayableAmount ?? 0).toLocaleString()}원
                    </p>
                  </div>
                </Card>
              </li>
            ))}
          </ul>

          <div className="flex flex-wrap justify-between items-center gap-3 pt-token-4 border-t border-border-default">
            <span className="text-sm text-text-muted">
              {page} / {totalPages} (총 {total}건)
            </span>
            <div className="flex gap-2 items-center">
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => handlePageChange(page - 1)}
                disabled={!hasPrevious}
              >
                이전
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => handlePageChange(page + 1)}
                disabled={!hasNext}
              >
                다음
              </Button>
            </div>
          </div>
        </>
      )}
    </div>
  );
};

export default OrderListPage;
