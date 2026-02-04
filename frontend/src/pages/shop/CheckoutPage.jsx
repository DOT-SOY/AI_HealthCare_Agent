import { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useCart } from '../../components/layout/ShopLayout';
import { getCart } from '../../services/cartApi';
import { createOrderFromCart, preparePayment, getOrderDetail } from '../../services/orderApi';
import { getMyAddressList } from '../../services/memberInfoAddrApi';

const TOSS_V1_URL = 'https://js.tosspayments.com/v1/payment.js';
const TOSS_V2_URL = 'https://js.tosspayments.com/v2/payment.js';
const getTossProxyUrl = () => (typeof window !== 'undefined' ? `${window.location.origin}/tosspayments-proxy/v2/standard` : '');

const loadScript = (url, runId) =>
  new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = url;
    script.async = true;
    script.onload = () => {
      resolve(window.TossPayments);
    };
    script.onerror = () => {
      reject(new Error(`Toss Payments 스크립트 로드 실패: ${url}`));
    };
    document.body.appendChild(script);
  });

const loadTossScript = () => {
  return new Promise((resolve, reject) => {
    if (typeof window !== 'undefined' && window.TossPayments) {
      resolve(window.TossPayments);
      return;
    }
    loadScript(TOSS_V1_URL, 'run')
      .then(resolve)
      .catch(() => {
        loadScript(TOSS_V2_URL, 'post-fix')
          .then(resolve)
          .catch(() => {
            const proxyUrl = getTossProxyUrl();
            return proxyUrl ? loadScript(proxyUrl, 'proxy') : Promise.reject(new Error('no proxy'));
          })
          .then(resolve)
          .catch(async () => {
            try {
              const mod = await import('@tosspayments/tosspayments-sdk');
              const loadTossPayments = mod.loadTossPayments ?? mod.default;
              const proxySrc = getTossProxyUrl();
              window.TossPayments = (clientKey) => ({
                requestPayment: async (method, opts) => {
                  const sdk = await loadTossPayments(clientKey, proxySrc ? { src: proxySrc } : undefined);
                  if (!sdk?.payment?.requestPayment) {
                    throw new Error('결제 스크립트를 불러오지 못했습니다. localhost/문서키 환경에서는 토스 CDN이 차단될 수 있어, 등록된 도메인과 연동키가 필요합니다.');
                  }
                  const methodCode = method === '카드' ? 'CARD' : method;
                  const amountObj = typeof opts.amount === 'number' ? { value: opts.amount, currency: 'KRW' } : opts.amount;
                  return sdk.payment.requestPayment({
                    method: methodCode,
                    amount: amountObj,
                    orderId: opts.orderId,
                    orderName: opts.orderName,
                    successUrl: opts.successUrl,
                    failUrl: opts.failUrl,
                  });
                },
              });
              resolve(window.TossPayments);
            } catch (e) {
              reject(e);
            }
          });
      });
  });
};

const CheckoutPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  // useCart는 ShopLayout 내부에서만 사용 가능 (Context)
  // ShopLayout은 ShopIndex에서 제공되므로 항상 사용 가능해야 함
  const cartContext = useCart();
  const { cartItems = [], totals = {} } = cartContext || {};
  const [cartSummary, setCartSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [form, setForm] = useState({
    shipTo: { recipientName: '', recipientPhone: '', zipcode: '', address1: '', address2: '' },
    buyer: { buyerName: '', buyerEmail: '', buyerPhone: '' },
    memo: '',
    paymentMethod: 'CARD',
  });
  const [checkoutPhase, setCheckoutPhase] = useState('form');
  const [widgetOrderPayload, setWidgetOrderPayload] = useState(null);
  const widgetInstanceRef = useRef(null);
  const [addressList, setAddressList] = useState([]);
  const [showAddressSelect, setShowAddressSelect] = useState(false);
  const defaultAppliedRef = useRef(false);
  const fromAIRef = useRef(false);
  const submitLockRef = useRef(false);

  const PAYMENT_METHODS = [
    { value: 'CARD', label: '카드(신용/체크/간편결제)', description: '네이버페이·카카오페이 등은 결제창에서 선택' },
    { value: 'TRANSFER', label: '계좌이체(실시간 이체)' },
    { value: 'VIRTUAL_ACCOUNT', label: '가상계좌' },
    { value: 'MOBILE_PHONE', label: '휴대폰' },
  ];

  const refreshCart = useCallback(async () => {
    try {
      const data = await getCart();
      setCartSummary(data);
    } catch {
      setCartSummary(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refreshCart();
  }, [refreshCart]);

  useEffect(() => {
    if (!localStorage.getItem('accessToken')) {
      navigate('/member/login', { state: { from: '/shop/checkout' }, replace: true });
      return;
    }
  }, [navigate]);

  /** 로그인 시 내 배송지 목록 조회 */
  useEffect(() => {
    if (!localStorage.getItem('accessToken')) return;
    getMyAddressList()
      .then((list) => setAddressList(Array.isArray(list) ? list : []))
      .catch(() => setAddressList([]));
  }, []);

  useEffect(() => {
    if (addressList.length === 0 || defaultAppliedRef.current) return;
    const defaultAddr = addressList.find((a) => a.isDefault);
    if (defaultAddr) {
      setForm((prev) => ({
        ...prev,
        shipTo: {
          recipientName: defaultAddr.shipToName ?? '',
          recipientPhone: defaultAddr.shipToPhone ?? '',
          zipcode: defaultAddr.shipZipcode ?? '',
          address1: defaultAddr.shipAddress1 ?? '',
          address2: defaultAddr.shipAddress2 ?? '',
        },
      }));
      defaultAppliedRef.current = true;
    }
  }, [addressList]);

  /** AI에서 온 경우: 주문 정보 자동 처리 및 결제 위젯 렌더링 */
  useEffect(() => {
    const state = location.state;
    if (state?.fromAI && state?.orderNo && state?.paymentReady && !fromAIRef.current) {
      fromAIRef.current = true;
      
      const { orderNo, paymentReady } = state;

      const loadOrderAndInitPayment = async () => {
        try {
          const orderDetail = await getOrderDetail(orderNo);

          if (orderDetail.shipTo) {
            setForm((prev) => ({
              ...prev,
              shipTo: {
                recipientName: orderDetail.shipTo.recipientName || '',
                recipientPhone: orderDetail.shipTo.recipientPhone || '',
                zipcode: orderDetail.shipTo.zipcode || '',
                address1: orderDetail.shipTo.address1 || '',
                address2: orderDetail.shipTo.address2 || '',
              },
            }));
          }
          
          if (orderDetail.buyer) {
            setForm((prev) => ({
              ...prev,
              buyer: {
                buyerName: orderDetail.buyer.name || '',
                buyerEmail: orderDetail.buyer.email || '',
                buyerPhone: orderDetail.buyer.phone || '',
              },
            }));
          }

          const clientKey = paymentReady?.clientKey ?? '';
          const customerKey = paymentReady?.customerKey ?? `guest-${orderNo}`;
          const orderName = paymentReady?.orderName ?? `주문 ${orderNo}`;
          const amountNumber = typeof paymentReady?.amount === 'number' 
            ? paymentReady.amount 
            : Number(paymentReady?.amount || 0);
          const orderIdStr = typeof orderNo === 'string' ? orderNo : String(orderNo);
          const baseUrl = window.location.origin + '/shop';
          const successUrl = `${baseUrl}/payment/success`;
          const failUrl = `${baseUrl}/payment/fail`;

          const getTossPayments = await loadTossScript();
          const raw = getTossPayments ? getTossPayments(clientKey) : window.TossPayments?.(clientKey);
          const sdk = await Promise.resolve(raw);

          if (sdk?.widgets && customerKey) {
            const widgets = sdk.widgets({ customerKey });
            await widgets.setAmount({ currency: 'KRW', value: amountNumber });
            await widgets.renderPaymentMethods({ selector: '#toss-payment-method' });
            await widgets.renderAgreement({ selector: '#toss-agreement' });
            widgetInstanceRef.current = widgets;
            
            setWidgetOrderPayload({
              orderId: orderIdStr,
              orderName,
              successUrl,
              failUrl,
              customerName: orderDetail.buyer?.name || undefined,
              customerEmail: orderDetail.buyer?.email || undefined,
              customerMobilePhone: orderDetail.buyer?.phone?.replace(/\D/g, '') || undefined,
            });
            setCheckoutPhase('widget_ready');
            setLoading(false);
          } else {
            console.error('결제 위젯을 초기화할 수 없습니다.');
            setLoading(false);
          }
        } catch (err) {
          console.error('주문 정보 불러오기 또는 결제 위젯 초기화 실패:', err);
          setLoading(false);
        }
      };

      loadOrderAndInitPayment();
    }
  }, [location.state]);

  const handleChange = (section, field, value) => {
    setForm((prev) => ({
      ...prev,
      [section]: { ...prev[section], [field]: value },
    }));
  };

  const applyAddressToForm = (addr) => {
    if (!addr) return;
    setForm((prev) => ({
      ...prev,
      shipTo: {
        recipientName: addr.shipToName ?? '',
        recipientPhone: addr.shipToPhone ?? '',
        zipcode: addr.shipZipcode ?? '',
        address1: addr.shipAddress1 ?? '',
        address2: addr.shipAddress2 ?? '',
      },
    }));
    setShowAddressSelect(false);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (submitLockRef.current || submitting) return;
    submitLockRef.current = true;

    if (checkoutPhase === 'widget_ready' && widgetInstanceRef.current && widgetOrderPayload) {
      setSubmitting(true);
      try {
        await widgetInstanceRef.current.requestPayment({
          orderId: widgetOrderPayload.orderId,
          orderName: widgetOrderPayload.orderName,
          successUrl: widgetOrderPayload.successUrl,
          failUrl: widgetOrderPayload.failUrl,
          customerName: widgetOrderPayload.customerName || undefined,
          customerEmail: widgetOrderPayload.customerEmail || undefined,
          customerMobilePhone: widgetOrderPayload.customerMobilePhone || undefined,
        });
      } catch (err) {
        console.error(err);
        alert(err?.message ?? '결제 요청 중 오류가 발생했습니다.');
      } finally {
        submitLockRef.current = false;
        setSubmitting(false);
      }
      return;
    }

    const items = cartSummary?.items ?? cartItems ?? [];
    if (!items.length) {
      alert('장바구니가 비어 있습니다.');
      return;
    }
    if (!form.shipTo.recipientName?.trim() || !form.shipTo.recipientPhone?.trim() || !form.shipTo.zipcode?.trim() || !form.shipTo.address1?.trim()) {
      alert('배송지(수령인, 연락처, 우편번호, 주소)를 모두 입력해주세요.');
      return;
    }
    if (!form.buyer.buyerName?.trim() || !form.buyer.buyerPhone?.trim()) {
      alert('주문자 이름과 연락처를 입력해주세요.');
      return;
    }

    setSubmitting(true);
    try {
      const orderRes = await createOrderFromCart({
        shipTo: form.shipTo,
        buyer: form.buyer,
        memo: form.memo || undefined,
      });
      const orderNo = orderRes?.orderNo;
      const amount = orderRes?.amount;
      if (!orderNo || amount == null) {
        throw new Error('주문 생성 응답이 올바르지 않습니다.');
      }

      const readyRes = await preparePayment(orderNo);
      const clientKey = readyRes?.clientKey ?? '';
      const customerKey = readyRes?.customerKey ?? `guest-${orderNo}`;
      const orderName = readyRes?.orderName ?? `주문 ${orderNo}`;
      const amountNumber = typeof amount === 'number' ? amount : Number(amount);
      const baseUrl = window.location.origin + '/shop';
      const successUrl = `${baseUrl}/payment/success`;
      const failUrl = `${baseUrl}/payment/fail`;
      const orderIdStr = typeof orderNo === 'string' ? orderNo : String(orderNo);

      const getTossPayments = await loadTossScript();
      const raw = getTossPayments ? getTossPayments(clientKey) : window.TossPayments?.(clientKey);
      const sdk = await Promise.resolve(raw);

      // 결제위젯 연동 키(문서용 테스트키 gck): sdk.widgets() 사용. docs.tosspayments.com/guides/v2/payment-widget/integration
      if (sdk?.widgets && customerKey) {
        const hadPrevInstance = !!widgetInstanceRef.current;
        let widgets = widgetInstanceRef.current;
        try {
          if (!widgets) {
            widgets = sdk.widgets({ customerKey });
          }
          await widgets.setAmount({ currency: 'KRW', value: amountNumber });
          if (!hadPrevInstance) {
            await widgets.renderPaymentMethods({ selector: '#toss-payment-method' });
            await widgets.renderAgreement({ selector: '#toss-agreement' });
          }
          widgetInstanceRef.current = widgets;
        } catch (err) {
          throw err;
        }
        setWidgetOrderPayload({
          orderId: orderIdStr,
          orderName,
          successUrl,
          failUrl,
          customerName: form.buyer?.buyerName || undefined,
          customerEmail: form.buyer?.buyerEmail || undefined,
          customerMobilePhone: form.buyer?.buyerPhone?.replace(/\D/g, '') || undefined,
        });
        setCheckoutPhase('widget_ready');
        setSubmitting(false);
        return;
      }

      const method = form.paymentMethod ?? 'CARD';
      if (sdk?.payment) {
        const payment = sdk.payment({ customerKey });
        await payment.requestPayment({
          method,
          amount: { value: amountNumber, currency: 'KRW' },
          orderId: orderIdStr,
          orderName,
          successUrl,
          failUrl,
          customerName: form.buyer?.buyerName || undefined,
          customerEmail: form.buyer?.buyerEmail || undefined,
          customerMobilePhone: form.buyer?.buyerPhone?.replace(/\D/g, '') || undefined,
        });
        return;
      }

      // v1 CDN: sdk.requestPayment(method, { amount, orderId, orderName, successUrl, failUrl })
      if (sdk?.requestPayment) {
        await sdk.requestPayment(method, {
          amount: amountNumber,
          orderId: orderNo,
          orderName,
          successUrl,
          failUrl,
        });
        return;
      }

      throw new Error('결제 SDK를 불러오지 못했습니다. (requestPayment 없음)');
    } catch (err) {
      console.error(err);
      alert(err?.message ?? '결제 준비 중 오류가 발생했습니다.');
    } finally {
      submitLockRef.current = false;
      setSubmitting(false);
    }
  };

  if (loading && !cartSummary) {
    return (
      <div className="flex items-center justify-center min-h-[200px]">
        <p className="text-text-muted">장바구니 불러오는 중...</p>
      </div>
    );
  }

  const items = cartSummary?.items ?? cartItems ?? [];
  const totalPrice = cartSummary?.totals?.totalPrice ?? totals?.totalPrice ?? 0;
  const totalQty = cartSummary?.totals?.totalQty ?? items.reduce((s, i) => s + (i.qty ?? 0), 0);

  return (
    <div className="max-w-2xl mx-auto text-text-main">
      <header className="section-header-token mb-8">
        <h1 className="section-title">주문/결제</h1>
        <p className="section-desc">배송지와 결제 수단을 확인한 뒤 결제해주세요.</p>
      </header>

      <section className="mb-8">
        <h2 className="text-lg font-semibold mb-3 text-text-main">주문 요약</h2>
        {items.length === 0 ? (
          <p className="text-text-muted">장바구니가 비어 있습니다. 상품을 담은 뒤 결제해주세요.</p>
        ) : (
          <div className="border border-border-default rounded-token p-4 bg-bg-card">
            <ul className="space-y-2 mb-3">
              {items.map((item) => (
                <li key={item.itemId} className="flex justify-between text-sm text-text-main">
                  <span>{item.productName ?? '-'} x {(item.qty ?? 0)}</span>
                  <span className="text-text-sub">{(Number(item.price ?? 0) * (item.qty ?? 1)).toLocaleString()}원</span>
                </li>
              ))}
            </ul>
            <div className="flex justify-between font-semibold pt-2 border-t border-border-default text-text-main">
              <span>총 수량 {totalQty}개 / 결제 금액</span>
              <span>{Number(totalPrice).toLocaleString()}원</span>
            </div>
          </div>
        )}
      </section>

      <form onSubmit={handleSubmit} className="space-y-6">
        <section className="bg-bg-card border border-border-default rounded-token p-6">
          <div className="flex items-center justify-between gap-2 mb-3">
            <h2 className="text-lg font-semibold text-text-main">배송지</h2>
            <div className="relative">
              <button
                type="button"
                disabled={addressList.length === 0}
                onClick={() => setShowAddressSelect((v) => !v)}
                className="px-3 py-1.5 text-sm border border-border-default rounded-token bg-bg-card text-text-main hover:border-primary-500 hover:text-primary-500 hover:bg-primary-500/10 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                배송지 불러오기
              </button>
              {showAddressSelect && addressList.length > 0 && (
                <div className="absolute right-0 top-full mt-1 z-10 min-w-[240px] border border-border-default rounded-token bg-bg-card shadow-card py-1 max-h-48 overflow-auto">
                  {addressList.map((addr) => (
                    <button
                      key={addr.id}
                      type="button"
                      onClick={() => applyAddressToForm(addr)}
                      className="w-full text-left px-3 py-2 text-sm text-text-main hover:bg-gray-100"
                    >
                      <span className="font-medium">{addr.shipToName}</span>
                      {addr.isDefault && (
                        <span className="ml-1 text-xs text-text-muted">(기본)</span>
                      )}
                      <br />
                      <span className="text-text-sub">
                        {[addr.shipAddress1, addr.shipAddress2].filter(Boolean).join(' ')}
                      </span>
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>
          <div className="space-y-3">
            <div>
              <label className="block text-sm font-medium text-text-main mb-1">수령인</label>
              <input
                type="text"
                required
                className="input-token w-full"
                value={form.shipTo.recipientName}
                onChange={(e) => handleChange('shipTo', 'recipientName', e.target.value)}
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-text-main mb-1">연락처</label>
              <input
                type="tel"
                required
                className="input-token w-full"
                value={form.shipTo.recipientPhone}
                onChange={(e) => handleChange('shipTo', 'recipientPhone', e.target.value)}
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-text-main mb-1">우편번호</label>
              <input
                type="text"
                required
                className="input-token w-full"
                value={form.shipTo.zipcode}
                onChange={(e) => handleChange('shipTo', 'zipcode', e.target.value)}
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-text-main mb-1">주소</label>
              <input
                type="text"
                required
                className="input-token w-full"
                value={form.shipTo.address1}
                onChange={(e) => handleChange('shipTo', 'address1', e.target.value)}
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-text-main mb-1">상세주소</label>
              <input
                type="text"
                className="input-token w-full"
                placeholder="상세주소 (선택)"
                value={form.shipTo.address2}
                onChange={(e) => handleChange('shipTo', 'address2', e.target.value)}
              />
            </div>
          </div>
        </section>

        <section className="bg-bg-card border border-border-default rounded-token p-6">
          <h2 className="text-lg font-semibold mb-3 text-text-main">주문자 정보</h2>
          <div className="space-y-3">
            <div>
              <label className="block text-sm font-medium text-text-main mb-1">주문자 이름</label>
              <input
                type="text"
                required
                className="input-token w-full"
                value={form.buyer.buyerName}
                onChange={(e) => handleChange('buyer', 'buyerName', e.target.value)}
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-text-main mb-1">이메일</label>
              <input
                type="email"
                className="input-token w-full"
                placeholder="선택 (영수증 발송용)"
                value={form.buyer.buyerEmail}
                onChange={(e) => handleChange('buyer', 'buyerEmail', e.target.value)}
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-text-main mb-1">연락처</label>
              <input
                type="tel"
                required
                className="input-token w-full"
                value={form.buyer.buyerPhone}
                onChange={(e) => handleChange('buyer', 'buyerPhone', e.target.value)}
              />
            </div>
          </div>
        </section>

        <section className="bg-bg-card border border-border-default rounded-token p-6">
          <label className="block text-sm font-medium text-text-main mb-1">배송 메모</label>
          <input
            type="text"
            className="input-token w-full"
            placeholder="배송 시 요청사항 (선택)"
            value={form.memo}
            onChange={(e) => setForm((p) => ({ ...p, memo: e.target.value }))}
          />
        </section>

        <section aria-label="결제 수단" className="bg-bg-card border border-border-default rounded-token p-6">
          <h2 className="text-lg font-semibold mb-3 text-text-main">결제 수단</h2>
          {checkoutPhase === 'widget_ready' && (
            <p className="text-sm text-text-muted mb-2">토스 결제 위젯에서 결제 수단을 선택하고 약관에 동의한 뒤 아래 결제하기를 눌러주세요.</p>
          )}
          <div id="toss-payment-method" className="min-h-[80px]" />
          <div id="toss-agreement" className="min-h-[60px] mt-3" />
        </section>

        <div className="flex gap-3 pt-4">
          <button
            type="button"
            onClick={() => {
              if (checkoutPhase === 'widget_ready') {
                setCheckoutPhase('form');
                setWidgetOrderPayload(null);
              } else {
                navigate(-1);
              }
            }}
            className="px-4 py-2 border border-border-default rounded-token bg-bg-card text-text-main hover:border-primary-500 hover:text-primary-500 hover:bg-primary-500/10 transition-colors"
          >
            {checkoutPhase === 'widget_ready' ? '결제 수단 다시 선택' : '이전'}
          </button>
          <button
            type="submit"
            disabled={submitting || (checkoutPhase === 'form' && items.length === 0)}
            className="flex-1 py-3 bg-primary-500 text-bg-root rounded-token hover:shadow-glow disabled:opacity-50 disabled:cursor-not-allowed font-medium border border-primary-500 transition-all"
          >
            {submitting ? '처리 중...' : '결제하기'}
          </button>
        </div>
      </form>
    </div>
  );
};

export default CheckoutPage;
