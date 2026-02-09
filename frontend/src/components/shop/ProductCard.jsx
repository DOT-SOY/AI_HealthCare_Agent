import { Link } from 'react-router-dom';
import Card from '../common/Card';

const ProductCard = ({
  product,
  displayPrice,
  getPrimaryImageUrl,
  ...rest
}) => {
  const imageUrl = getPrimaryImageUrl
    ? getPrimaryImageUrl(product)
    : product.images?.[0]?.url || 'https://via.placeholder.com/300x300?text=No+Image';

  return (
    <Card className="group overflow-hidden flex flex-col h-full transition-all duration-300 ease-out-quart hover:-translate-y-1 hover:shadow-card-hover border-gray-100">
      {/* 이미지 — 클릭 시 상세 이동 */}
      <Link
        to={`/shop/detail/${product.id}`}
        className="block flex-shrink-0 relative overflow-hidden aspect-square bg-bg-surface"
      >
        <img
          src={imageUrl}
          alt={product.name}
          className="absolute inset-0 w-full h-full object-cover transition-transform duration-500 ease-out-quart group-hover:scale-105"
          onError={(e) => {
            e.target.src = 'https://via.placeholder.com/300x300?text=No+Image';
          }}
        />
        {product.status !== 'ACTIVE' && (
          <span
            className={`absolute top-2 right-2 px-2 py-0.5 rounded-token-sm text-xs font-semibold uppercase ${
              product.status === 'SOLD_OUT'
                ? 'bg-accent-secondary/90 text-white border border-accent-secondary'
                : 'bg-gray-500/90 text-white border border-gray-400'
            }`}
          >
            {product.status === 'SOLD_OUT' ? '품절' : '임시저장'}
          </span>
        )}
      </Link>

      <div className="flex flex-col flex-1 px-3 py-3 min-h-0">
        <Link to={`/shop/detail/${product.id}`} className="block flex-1 min-h-0">
          <h3 className="font-semibold text-[23px] text-text-main line-clamp-2 leading-snug group-hover:text-primary-400 transition-colors duration-200">
            {product.name}
          </h3>
        </Link>
        <div className="flex items-baseline gap-0.5 mt-1">
          <span className="text-xl font-bold text-primary-500 font-emphasis tracking-tight">
            {displayPrice != null ? displayPrice.toLocaleString() : '-'}
          </span>
          <span className="text-sm text-primary-500/90 font-medium">원</span>
        </div>
      </div>
    </Card>
  );
};

export default ProductCard;
