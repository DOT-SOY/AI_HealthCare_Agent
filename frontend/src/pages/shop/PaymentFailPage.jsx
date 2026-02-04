import { useSearchParams, Link } from 'react-router-dom';

const PaymentFailPage = () => {
  const [searchParams] = useSearchParams();
  const code = searchParams.get('code') ?? '';
  const message = searchParams.get('message') ?? '결제에 실패했습니다.';

  return (
    <div className="max-w-lg mx-auto text-center py-16">
      <h1 className="text-xl font-bold text-accent-secondary mb-2">결제 실패</h1>
      {code && <p className="text-sm text-text-muted mb-1">코드: {code}</p>}
      <p className="text-text-main mb-6">{message}</p>
      <div className="flex justify-center gap-3 flex-wrap">
        <Link
          to="/shop/checkout"
          className="inline-block px-6 py-2 bg-primary-500 text-bg-root rounded-token hover:shadow-glow font-medium border border-primary-500 transition-all"
        >
          결제 다시 시도
        </Link>
        <Link
          to="/shop/list"
          className="inline-block px-6 py-2 border border-border-default rounded-token bg-bg-card text-text-main hover:border-primary-500 hover:text-primary-500 hover:bg-primary-500/10 font-medium transition-colors"
        >
          쇼핑 계속하기
        </Link>
      </div>
    </div>
  );
};

export default PaymentFailPage;
