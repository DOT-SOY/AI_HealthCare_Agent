import { Link } from 'react-router-dom';
import Card from '../common/Card';
import Button from '../common/Button';
import QtyStepper from '../cart/QtyStepper';

/**
 * 쇼핑몰 전용 상품 카드
 * - 토큰만 사용 (색상/px/shadow 하드코딩 금지)
 * - 이미지, 상품명, 가격(primary), 장바구니 버튼
 */
const ProductCard = ({
  product,
  displayPrice,
  selectedVariant,
  onVariantChange,
  qty,
  onQtyChange,
  onAddToCart,
  isAddingDisabled,
  getPrimaryImageUrl,
}) => {
  const hasVariants = product.variants && product.variants.filter((v) => v.active).length > 0;
  const imageUrl = getPrimaryImageUrl
    ? getPrimaryImageUrl(product)
    : product.images?.[0]?.url || 'https://via.placeholder.com/300x300?text=No+Image';

  return (
    <Card className="overflow-hidden flex flex-col hover:shadow-card-hover transition-shadow">
      <Link to={`/shop/detail/${product.id}`} className="block flex-shrink-0">
        <div className="aspect-square bg-bg-surface overflow-hidden">
          <img
            src={imageUrl}
            alt={product.name}
            className="w-full h-full object-cover"
            onError={(e) => {
              e.target.src = 'https://via.placeholder.com/300x300?text=No+Image';
            }}
          />
        </div>
        <div className="p-4">
          <h3 className="font-semibold text-lg text-text-main mb-2 line-clamp-2">{product.name}</h3>
          <p className="text-text-sub text-sm mb-3 line-clamp-2">{product.description}</p>
          <div className="flex justify-between items-center mb-3">
            <span className="text-2xl font-bold text-primary-500">
              {displayPrice != null ? displayPrice.toLocaleString() : '-'}원
            </span>
            <span
              className={`px-2 py-1 rounded text-xs ${
                product.status === 'ACTIVE'
                  ? 'bg-primary-500/20 text-primary-400'
                  : 'bg-bg-surface text-text-muted'
              }`}
            >
              {product.status === 'ACTIVE' ? '판매중' : '품절'}
            </span>
          </div>
        </div>
      </Link>
      <div className="p-4 pt-0 mt-auto border-t border-border-default">
        {hasVariants && (
          <div className="mb-3">
            <span className="text-sm text-text-sub mb-2 block">옵션:</span>
            <div className="flex flex-wrap gap-2">
              {product.variants
                .filter((v) => v.active)
                .map((v) => {
                  const label = v.optionText || `옵션 #${v.id}`;
                  const isSelected = selectedVariant?.id === v.id;
                  return (
                    <button
                      key={v.id}
                      type="button"
                      onClick={(e) => {
                        e.preventDefault();
                        e.stopPropagation();
                        onVariantChange?.(product.id, isSelected ? null : v);
                      }}
                      className={`px-3 py-1 rounded text-xs font-medium transition border ${
                        isSelected
                          ? 'bg-primary-500 text-bg-root border-primary-500'
                          : 'bg-bg-card text-text-main border-border-default hover:border-primary-500'
                      }`}
                    >
                      {label}
                    </button>
                  );
                })}
            </div>
          </div>
        )}
        <div className="flex items-center gap-2 mb-2">
          <span className="text-sm text-text-sub">수량:</span>
          <QtyStepper
            value={qty ?? 1}
            onChange={(newQty) => onQtyChange?.(product.id, newQty)}
            disabled={product.status !== 'ACTIVE'}
            buttonClassName="border-border-default bg-bg-card text-text-main hover:bg-bg-surface disabled:opacity-50"
            valueClassName="text-text-main"
          />
        </div>
        <Button
          type="button"
          variant="primary"
          size="md"
          className="w-full"
          onClick={(e) => onAddToCart?.(e, product)}
          disabled={isAddingDisabled}
        >
          담기
        </Button>
      </div>
    </Card>
  );
};

export default ProductCard;
