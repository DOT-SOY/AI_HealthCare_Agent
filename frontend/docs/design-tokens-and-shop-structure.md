# 전역 디자인 토큰 및 쇼핑몰 적용 구조

## 1. 전역 디자인 토큰

### 1.1 정의 위치

- **CSS 변수**: `src/styles/design-tokens.css`
  - `:root`와 `[data-theme="dark"]`에 동일 값으로 정의
  - **전체 페이지 통일**: `BasicLayout`이 `<main>` 안에 `data-theme="dark"` + `page-root` + `page-container`를 적용하여, Home / Routine / Record / Meal / Ranking / Profile / Admin 등 모든 페이지가 shop list와 동일한 다크·라임 톤을 사용
  - member(로그인·회원가입·수정·카카오 리다이렉트)는 각 페이지에서 `data-theme="dark"` + `page-root`·`page-container` 적용

- **Tailwind 확장**: `tailwind.config.js`의 `theme.extend`
  - `colors`, `boxShadow`에 토큰 매핑 → `bg-bg-root`, `text-primary-500`, `shadow-card`, `shadow-glow` 등 유틸리티 사용 가능

### 1.2 토큰 목록

| 용도 | CSS 변수 | 값(참고) | Tailwind 예시 |
|------|----------|----------|----------------|
| 배경 | `--bg-root`, `--bg-surface`, `--bg-card` | #0B0B0B, #242424, #1E1E1E | `bg-bg-root`, `bg-bg-surface`, `bg-bg-card` |
| Primary (라임) | `--primary-500`, `--primary-400`, `--primary-glow` | #B6FF00 등 | `text-primary-500`, `bg-primary-500`, `shadow-glow` |
| 텍스트 | `--text-main`, `--text-sub`, `--text-muted` | — | `text-text-main`, `text-text-sub`, `text-text-muted` |
| 테두리 | `--border-default` | — | `border-border-default` |
| 그림자 | (config) | — | `shadow-card`, `shadow-card-hover`, `shadow-glow` |
| 전역 폰트 | `--font-sans` | Pretendard (전체 통일) | `font-sans` |
| 강조/디스플레이 | `--font-emphasis`, `--font-display` | Pretendard (동일) | `font-emphasis`, `font-display` |
| Radius | `--radius-sm/md/lg` | 8px, 12px, 15px | `rounded-token-sm`, `rounded-token`, `rounded-token-lg` |
| Spacing | `--spacing-token-2/4/6/8`, `--gap-section` | 0.5rem~2rem | `p-token-4`, `mb-token-8`, `space-y-section` |
| Gap | `--gap-grid`, `--gap-section` | 1.5rem, 2rem | `gap-grid`, `gap-section`, `gap-token-2` |

### 1.3 전역 컴포넌트 클래스 (`src/styles/components.css`)

다른 페이지에서도 그대로 쓸 수 있는 **전역 Tailwind 컴포넌트 클래스** (`@layer components`):

| 클래스 | 용도 | 사용 예 |
|--------|------|--------|
| `container-token` | 가로 패딩 + 가운데 정렬 (sm/lg 브레이크포인트) | 섹션 래퍼, 리스트·상세 공통 |
| `section-token` | 섹션 가로 패딩만 (전체 너비 영역용) | 풀폭 섹션 |
| `input-token` | 검색/폼 입력창 공통 스타일 (높이·테두리·포커스 링) | 검색창, 로그인 입력 등 |
| `input-token-with-icon` | 아이콘 있는 입력창 왼쪽 여백 | 검색 아이콘 왼쪽 배치 시 |
| `card-token` | 카드 베이스 (컴포넌트 없이 클래스만 쓸 때) | `Card.jsx` 대신 클래스만 쓰는 경우 |
| `segment-btn` / `segment-btn-active` | 세그먼트·칩 버튼 (카테고리 필터 등) | `segment-btn` + 선택 시 `segment-btn-active` |
| `page-btn` / `page-btn-active` | 페이지네이션 숫자 버튼 | `page-btn` + 현재 페이지 `page-btn-active` |
| `spinner-token` | 로딩 스피너 (원형) | 로딩 UI |
| `section-header-token` | 섹션 헤더 래퍼 | 내부에 `.section-title`, `.section-desc` 사용 |
| `page-root` | 전체 페이지 래퍼 (배경·최소 높이) | `bg-bg-root`, `min-h-screen` |
| `page-container` | 페이지 콘텐츠 래퍼 (max-width 80rem·패딩) | BasicLayout 내부 또는 member 페이지 |
| `page-container-wide` | 쇼핑 본문용 넓은 래퍼 (max-width 96rem) | ShopLayout 본문 |
| `ui-card`, `ui-title`, `ui-input`, `ui-btn-primary` 등 | 폼/로그인·회원가입 카드·입력·버튼 | LoginComponent, JoinComponent, ModifyComponent |
| Tailwind `baseBg`, `baseMuted`, `baseBorder`, `baseSurface` | member 폼 호환 색상 (토큰과 동일) | `bg-baseBg`, `text-baseMuted` 등 |

- **버튼/카드**는 가능하면 `Button.jsx`, `Card.jsx` 사용. 클래스만 필요할 때 `card-token`, 세그먼트/페이지 버튼은 위 클래스 사용.

### 1.4 공통 UI 규칙

- **Card**: dark surface + soft shadow → `Card` 컴포넌트 사용 (`bg-bg-card`, `shadow-card`, `border-border-default`)
- **Button**
  - primary: neon lime + hover glow → `variant="primary"`
  - ghost: dark bg + lime border → `variant="ghost"`
- **가격/CTA**: 무조건 primary 컬러만 사용 (`text-primary-500`, `Button variant="primary"`)

### 1.5 앱/대시보드 전역 (Profile·Admin 등)

- **색상 변수**: `src/styles/app-tokens.css`
  - `:root`(라이트), `[data-theme="dark"]`(다크) — 컨테이너에 `data-theme="light"` | `"dark"` 로 제어
  - 변수: `--bg-primary`, `--bg-secondary`, `--text-primary`, `--text-secondary`, `--accent-lime`, `--btn-logout-bg`, `--border-color`

- **대시보드 컴포넌트 클래스**: `src/styles/components.css` 내 동일 `@layer components`
  - 레이아웃: `dashboard-container`, `dashboard-header`, `header-right`, `dashboard-main`, `left-sidebar`, `right-content`
  - 카드/정보: `info-card`, `card-header`, `info-card .section-title`, `btn-edit`, `data-list`, `data-row`, `profile-details`, `grey-info-box`, `info-title`, `info-value`
  - 차트/배지: `charts-container`, `chart-layout-row`, `chart-area`, `chart-main-title`, `badge-row`, `lime-badge`
  - 버튼: `btn-toggle-theme`, `btn-logout`, `icon-home`

- **페이지 전용 CSS 파일 제거**: 기존 `Profile.css`는 위 전역으로 이전 완료. 다른 페이지에서도 동일 클래스 사용 가능.

---

## 2. 쇼핑몰에만 토큰 적용하는 구조

### 2.1 적용 범위

- **래퍼**: `ShopLayout`에서 최상위에 `data-theme="dark"` + `bg-bg-root` 적용
- **페이지**: `pages/shop/` (ListPage, DetailPage 등)
- **컴포넌트**: `components/shop/` (ProductCard), `components/cart/` (CartDrawer, FloatingCartButton)
- **공통 컴포넌트**: `components/common/Card.jsx`, `components/common/Button.jsx` — 토큰 기반으로 작성되어 **쇼핑몰에서만** 이 스타일이 보이도록 사용

### 2.2 폴더 구조

```
src/
├── styles/
│   └── design-tokens.css    # CSS 변수 + @theme (Tailwind v4)
├── components/
│   ├── common/              # 토큰 기반 공통 컴포넌트 (다른 페이지에서도 재사용 가능)
│   │   ├── Card.jsx
│   │   └── Button.jsx
│   ├── shop/                # 쇼핑몰 전용
│   │   └── ProductCard.jsx
│   ├── cart/                # 쇼핑몰에서 사용, 토큰 적용
│   │   ├── CartDrawer.jsx
│   │   ├── FloatingCartButton.jsx
│   │   └── QtyStepper.jsx
│   └── layout/
│       └── ShopLayout.jsx    # data-theme="dark" + bg-bg-root 적용
├── pages/
│   └── shop/                # ListPage, DetailPage 등 — 토큰/공통 컴포넌트만 사용
│       ├── ListPage.jsx
│       └── DetailPage.jsx
└── index.css                # design-tokens.css import
```

### 2.3 다른 페이지에 토큰 그대로 적용하는 방법

1. **해당 영역을 토큰 테마로 쓰고 싶을 때**
   - 그 페이지를 감싸는 레이아웃 또는 최상위 div에 `data-theme="dark"` 추가
   - 배경은 `bg-bg-root` 또는 `bg-bg-surface` 사용
   - 텍스트/버튼/카드는 `text-text-main`, `Card`, `Button` 등 토큰·공통 컴포넌트 사용

2. **공통 컴포넌트만 쓰고 색은 기존 유지**
   - `Card`, `Button`은 토큰 색을 쓰므로, **토큰 테마 래퍼 밖**에서 쓰면 CSS 변수 값이 `:root`와 동일해 전역에 노출됨
   - 다른 페이지에서 “기존 라이트/다른 색”을 유지하려면:
     - 해당 페이지는 `data-theme="dark"` 밖에 두고,
     - 그 페이지 전용 카드/버튼을 쓰거나,
     - 나중에 `[data-theme="light"]` 같은 별도 토큰 세트를 정의해 래퍼로 적용

3. **확장 시 권장**
   - 새 토큰은 `design-tokens.css`에 변수 추가 후 `tailwind.config.js`의 `theme.extend`에 동일 이름으로 매핑
   - 새 공통 컴포넌트는 `components/common/`에 두고, 색/간격/그림자는 토큰 클래스만 사용 (px/색상 하드코딩 금지)

---

## 3. 금지 사항 (유지)

- 디자인 토큰 값 임의 변경 금지
- 페이지 컴포넌트에 색상·px·shadow 하드코딩 금지 (전역 토큰 또는 공통 컴포넌트 사용)
- 인라인 스타일 사용 금지
