import { Routes, Route } from 'react-router-dom'
import Header from './pages/Header'
import AIChatOverlay from './pages/AIChatOverlay'
import TodayRoutinePage from './pages/TodayRoutinePage/TodayRoutinePage'
import HistoryPage from './pages/HistoryPage/HistoryPage'

function App() {
  return (
    <div className="min-h-screen bg-neutral-950">
      <Header />
      <Routes>
        <Route path="/" element={<TodayRoutinePage />} />
        <Route path="/routine" element={<TodayRoutinePage />} />
        <Route path="/history" element={<HistoryPage />} />
      </Routes>
      <AIChatOverlay />
    </div>
  )
}

export default App
