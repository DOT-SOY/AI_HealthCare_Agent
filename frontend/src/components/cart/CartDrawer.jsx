import QtyStepper from './QtyStepper';

// API 응답 형식: itemId, variantId, qty, productId, productName, price, optionSummary, primaryImageUrl
// onUpdateQty(itemId, qty), onRemoveItem(itemId) — DELETE/수정은 반드시 itemId로만 호출 (variantId 금지)
const CartDrawer = ({ isOpen, onClose, cartItems, totals, onUpdateQty, onRemoveItem }) => {
  const totalQty = totals?.totalQty ?? cartItems.reduce((sum, item) => sum + (item.qty ?? 0), 0);
  const totalPrice = totals?.totalPrice ?? cartItems.reduce((sum, item) => {
    const p = Number(item.price ?? 0);
    return sum + p * (item.qty ?? 0);
  }, 0);

  if (!isOpen) return null;

  return (
    <>
      <div
        className="fixed inset-0 bg-black bg-opacity-50 z-40 transition-opacity"
        onClick={onClose}
      />
      <div className="fixed top-0 right-0 h-full w-full max-w-md bg-white shadow-xl z-50 transform transition-transform duration-300 ease-in-out translate-x-0">
        <div className="flex flex-col h-full">
          <div className="flex items-center justify-between p-4 border-b">
            <h2 className="text-xl font-bold">장바구니</h2>
            <button type="button" onClick={onClose} className="p-2 hover:bg-gray-100 rounded-full transition">
              <svg xmlns="http://www.w3.org/2000/svg" className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          <div className="flex-1 overflow-y-auto p-4">
            {cartItems.length === 0 ? (
              <div className="flex flex-col items-center justify-center h-full text-gray-500">
                <svg xmlns="http://www.w3.org/2000/svg" className="h-16 w-16 mb-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z" />
                </svg>
                <p>장바구니가 비어있습니다</p>
              </div>
            ) : (
              <div className="space-y-4">
                {cartItems.map((item) => {
                  const price = Number(item.price ?? 0);
                  const imageUrl = item.primaryImageUrl ?? 'https://via.placeholder.com/100x100?text=No+Image';
                  const itemId = item.itemId;
                  if (!itemId) return null;
                  return (
                    <div key={itemId} className="flex gap-4 p-4 border rounded-lg">
                      <img
                        src={imageUrl}
                        alt={item.productName ?? ''}
                        className="w-20 h-20 object-cover rounded"
                        onError={(e) => { e.target.src = 'https://via.placeholder.com/100x100?text=No+Image'; }}
                      />
                      <div className="flex-1">
                        <h3 className="font-semibold mb-1">{item.productName ?? ''}</h3>
                        {item.optionSummary && (
                          <p className="text-sm text-gray-600 mb-2">옵션: {item.optionSummary}</p>
                        )}
                        <div className="flex items-center justify-between">
                          <div>
                            <p className="text-lg font-bold text-blue-600">{price.toLocaleString()}원</p>
                            <QtyStepper
                              value={item.qty ?? 1}
                              onChange={(newQty) => onUpdateQty(itemId, newQty)}
                            />
                          </div>
                          <button
                            type="button"
                            onClick={() => onRemoveItem(itemId)}
                            className="p-2 text-red-500 hover:bg-red-50 rounded transition"
                          >
                            <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                            </svg>
                          </button>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {cartItems.length > 0 && (
            <div className="border-t p-4 space-y-4">
              <div className="flex justify-between text-lg">
                <span className="font-semibold">총 수량</span>
                <span className="font-bold">{totalQty}개</span>
              </div>
              <div className="flex justify-between text-xl">
                <span className="font-semibold">총 금액</span>
                <span className="font-bold text-blue-600">{Number(totalPrice).toLocaleString()}원</span>
              </div>
              <button type="button" className="w-full py-3 bg-blue-500 text-white rounded-lg hover:bg-blue-600 transition font-medium">
                주문하기
              </button>
            </div>
          )}
        </div>
      </div>
    </>
  );
};

export default CartDrawer;
