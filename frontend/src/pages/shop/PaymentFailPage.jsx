import { useSearchParams, Link } from 'react-router-dom';

const PaymentFailPage = () => {
  const [searchParams] = useSearchParams();
  const code = searchParams.get('code') ?? '';
  const message = searchParams.get('message') ?? '결제에 실패했습니다.';

  return (
    <div className="max-w-lg mx-auto text-center py-16">
      <h1 className="text-xl font-bold text-red-600 mb-2">결제 실패</h1>
      {code && <p className="text-sm text-gray-500 mb-1">코드: {code}</p>}
      <p className="text-gray-700 mb-6">{message}</p>
      <Link to="/shop/checkout" className="text-blue-500 hover:underline mr-4">
        결제 다시 시도
      </Link>
      <Link to="/shop/list" className="text-blue-500 hover:underline">
        쇼핑 계속하기
      </Link>
    </div>
  );
};

export default PaymentFailPage;
