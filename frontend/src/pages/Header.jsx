import { NavLink } from 'react-router-dom';

export default function Header() {
  return (
    <div className="fixed left-0 top-0 h-full w-20 bg-neutral-900 flex flex-col items-center py-6 z-50">
      {/* 로고 */}
      <div className="mb-8">
        <div className="w-10 h-10 flex items-center justify-center">
          <span className="text-2xl" style={{ color: '#88ce02' }}>⚡</span>
        </div>
      </div>

      {/* 네비게이션 메뉴 */}
      <nav className="flex flex-col gap-6 w-full">
        <NavLink
          to="/"
          className={({ isActive }) =>
            `flex flex-col items-center gap-2 py-2 transition-colors ${
              isActive ? '' : 'text-neutral-400 hover:text-neutral-200'
            }`
          }
          style={({ isActive }) => isActive ? { color: '#88ce02' } : undefined}
        >
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
          </svg>
          <span className="text-xs">Home</span>
        </NavLink>

        <NavLink
          to="/routine"
          className={({ isActive }) =>
            `flex flex-col items-center gap-2 py-2 transition-colors ${
              isActive ? '' : 'text-neutral-400 hover:text-neutral-200'
            }`
          }
          style={({ isActive }) => isActive ? { color: '#88ce02' } : undefined}
        >
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <span className="text-xs">루틴</span>
        </NavLink>

        <NavLink
          to="/history"
          className={({ isActive }) =>
            `flex flex-col items-center gap-2 py-2 transition-colors ${
              isActive ? '' : 'text-neutral-400 hover:text-neutral-200'
            }`
          }
          style={({ isActive }) => isActive ? { color: '#88ce02' } : undefined}
        >
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
          </svg>
          <span className="text-xs">기록</span>
        </NavLink>

        <NavLink
          to="/meal"
          className={({ isActive }) =>
            `flex flex-col items-center gap-2 py-2 transition-colors ${
              isActive ? '' : 'text-neutral-400 hover:text-neutral-200'
            }`
          }
          style={({ isActive }) => isActive ? { color: '#88ce02' } : undefined}
        >
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6V4m0 2a2 2 0 100 4m0-4a2 2 0 110 4m-6 8a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4m6 6v10m6-2a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4" />
          </svg>
          <span className="text-xs">식사</span>
        </NavLink>

        <NavLink
          to="/shopping"
          className={({ isActive }) =>
            `flex flex-col items-center gap-2 py-2 transition-colors ${
              isActive ? '' : 'text-neutral-400 hover:text-neutral-200'
            }`
          }
          style={({ isActive }) => isActive ? { color: '#88ce02' } : undefined}
        >
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z" />
          </svg>
          <span className="text-xs">쇼핑</span>
        </NavLink>

        <NavLink
          to="/ranking"
          className={({ isActive }) =>
            `flex flex-col items-center gap-2 py-2 transition-colors ${
              isActive ? '' : 'text-neutral-400 hover:text-neutral-200'
            }`
          }
          style={({ isActive }) => isActive ? { color: '#88ce02' } : undefined}
        >
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11.049 2.927c.3-.921 1.603-.921 1.902 0l1.519 4.674a1 1 0 00.95.69h4.915c.969 0 1.371 1.24.588 1.81l-3.976 2.888a1 1 0 00-.363 1.118l1.518 4.674c.3.922-.755 1.688-1.538 1.118l-3.976-2.888a1 1 0 00-1.176 0l-3.976 2.888c-.783.57-1.838-.197-1.538-1.118l1.518-4.674a1 1 0 00-.363-1.118l-3.976-2.888c-.784-.57-.38-1.81.588-1.81h4.914a1 1 0 00.951-.69l1.519-4.674z" />
          </svg>
          <span className="text-xs">랭킹</span>
        </NavLink>

        <NavLink
          to="/profile"
          className={({ isActive }) =>
            `flex flex-col items-center gap-2 py-2 transition-colors ${
              isActive ? '' : 'text-neutral-400 hover:text-neutral-200'
            }`
          }
          style={({ isActive }) => isActive ? { color: '#88ce02' } : undefined}
        >
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
          </svg>
          <span className="text-xs">프로필</span>
        </NavLink>
      </nav>
    </div>
  );
}


