import { useState, useEffect } from 'react';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import { routineApi } from '../../api/routineApi';
import { useTheme } from '../../contexts/ThemeContext';

export default function VolumeChart() {
  const { theme } = useTheme();
  const [period, setPeriod] = useState('month'); // 'month' or 'week'
  const [data, setData] = useState({ current: [], previous: [] });
  const [loading, setLoading] = useState(true);

  // 테마에 따른 색상 설정
  const colors = {
    primary: theme === 'light' ? '#8FCC00' : '#B6FF00', // 연두색 (라이트: 더 어두운 색상)
    secondary: theme === 'light' ? '#737373' : '#e5e5e5', // 흰색/검정 (라이트: text-muted로 더 연하게)
    grid: theme === 'light' ? '#d4d4d4' : '#404040', // 그리드 색상
    axis: theme === 'light' ? '#525252' : '#a3a3a3', // 축 색상
    tooltipBg: theme === 'light' ? '#ffffff' : '#1a1a1a', // 툴팁 배경
    tooltipBorder: theme === 'light' ? '#e5e5e5' : '#404040', // 툴팁 테두리
    tooltipText: theme === 'light' ? '#171717' : '#e5e5e5', // 툴팁 텍스트
    legend: theme === 'light' ? '#171717' : '#e5e5e5', // 범례 색상
  };

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        const response = await routineApi.getVolumeStats(period);
        setData(response || { current: [], previous: [] });
      } catch (error) {
        console.error('볼륨 통계 조회 실패:', error);
        setData({ current: [], previous: [] });
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [period]);

  // 차트 데이터 준비
  const prepareChartData = () => {
    if (period === 'month') {
      // 월별: 이번 달의 모든 날짜 생성 (1일 ~ 마지막 날)
      const today = new Date();
      const currentYear = today.getFullYear();
      const currentMonth = today.getMonth();
      const daysInMonth = new Date(currentYear, currentMonth + 1, 0).getDate();
      
      // 이번 달 데이터 맵 생성
      const currentMap = new Map();
      data.current.forEach(point => {
        const date = new Date(point.date);
        const day = date.getDate();
        currentMap.set(day, point.totalVolume);
      });
      
      // 저번 달 데이터 맵 생성
      const previousMap = new Map();
      data.previous.forEach(point => {
        const date = new Date(point.date);
        const day = date.getDate();
        previousMap.set(day, point.totalVolume);
      });
      
      // 1일부터 마지막 날까지 데이터 생성
      const chartData = [];
      for (let day = 1; day <= daysInMonth; day++) {
        chartData.push({
          name: `${day}일`,
          current: currentMap.has(day) ? currentMap.get(day) : 0,
          previous: previousMap.has(day) ? previousMap.get(day) : 0,
        });
      }
      return chartData;
    } else {
      // 주별: 이번 주 월요일부터 일요일까지
      const today = new Date();
      const dayOfWeek = today.getDay(); // 0(일) ~ 6(토)
      // 월요일까지의 오프셋 계산: 일요일(0)이면 -6, 월요일(1)이면 0, 화요일(2)이면 -1, ...
      const mondayOffset = dayOfWeek === 0 ? -6 : 1 - dayOfWeek;
      
      const monday = new Date(today);
      monday.setDate(today.getDate() + mondayOffset);
      monday.setHours(0, 0, 0, 0); // 시간 초기화
      
      // 이번 주 데이터 맵 생성 (날짜 문자열을 키로)
      const currentMap = new Map();
      if (Array.isArray(data.current)) {
        data.current.forEach(point => {
          if (point && point.date) {
            const dateStr = typeof point.date === 'string' 
              ? point.date.split('T')[0] 
              : new Date(point.date).toISOString().split('T')[0];
            currentMap.set(dateStr, point.totalVolume);
          }
        });
      }
      
      // 저번 주 데이터 맵 생성
      const previousMap = new Map();
      if (Array.isArray(data.previous)) {
        data.previous.forEach(point => {
          if (point && point.date) {
            const dateStr = typeof point.date === 'string' 
              ? point.date.split('T')[0] 
              : new Date(point.date).toISOString().split('T')[0];
            previousMap.set(dateStr, point.totalVolume);
          }
        });
      }
      
      const weekdays = ['월', '화', '수', '목', '금', '토', '일'];
      const chartData = [];
      
      // 저번 주 월요일 계산 (이번 주보다 7일 전)
      const previousMonday = new Date(monday);
      previousMonday.setDate(monday.getDate() - 7);
      
      for (let i = 0; i < 7; i++) {
        // 이번 주 날짜
        const date = new Date(monday);
        date.setDate(monday.getDate() + i);
        const dateStr = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
        
        // 저번 주 같은 요일 날짜 (7일 전)
        const previousDate = new Date(previousMonday);
        previousDate.setDate(previousMonday.getDate() + i);
        const previousDateStr = `${previousDate.getFullYear()}-${String(previousDate.getMonth() + 1).padStart(2, '0')}-${String(previousDate.getDate()).padStart(2, '0')}`;
        
        chartData.push({
          name: weekdays[i],
          current: currentMap.has(dateStr) ? currentMap.get(dateStr) : 0,
          previous: previousMap.has(previousDateStr) ? previousMap.get(previousDateStr) : 0,
        });
      }
      
      return chartData;
    }
  };

  const chartData = prepareChartData();

  return (
    <div className="w-full h-full">
      {/* Period Selection */}
      <div className="flex gap-2 mb-4">
        <button
          onClick={() => setPeriod('month')}
          className={`px-4 py-1 rounded-token text-sm font-medium transition-colors ${
            period === 'month'
              ? 'bg-primary-500 text-bg-root'
              : 'bg-gray-100 text-text-main hover:bg-gray-200'
          }`}
        >
          월별
        </button>
        <button
          onClick={() => setPeriod('week')}
          className={`px-4 py-1 rounded-token text-sm font-medium transition-colors ${
            period === 'week'
              ? 'bg-primary-500 text-bg-root'
              : 'bg-gray-100 text-text-main hover:bg-gray-200'
          }`}
        >
          주별
        </button>
      </div>

      {/* Chart */}
      {loading ? (
        <div className="flex items-center justify-center h-64">
          <div className="spinner-token" />
        </div>
      ) : chartData.length > 0 ? (
        <ResponsiveContainer width="100%" height={340}>
          <LineChart 
            data={chartData}
            margin={{ top: 5, right: 20, left: 10, bottom: -20 }}
          >
            <CartesianGrid strokeDasharray="3 3" stroke={colors.grid} />
            <XAxis 
              dataKey="name" 
              stroke={colors.axis}
              style={{ fontSize: '11px' }}
              angle={period === 'month' ? -45 : 0}
              textAnchor={period === 'month' ? 'end' : 'middle'}
              height={period === 'month' ? 50 : 30}
              interval={period === 'month' ? 0 : 0} // 모든 레이블 표시
            />
            <YAxis 
              stroke={colors.axis}
              style={{ fontSize: '12px' }}
            />
            <Tooltip 
              contentStyle={{ 
                backgroundColor: colors.tooltipBg, 
                border: `1px solid ${colors.tooltipBorder}`,
                borderRadius: '8px',
                color: colors.tooltipText
              }}
            />
            <Legend 
              wrapperStyle={{ color: colors.legend, paddingTop: '0px', marginTop: '-15px', display: 'flex', gap: '20px', justifyContent: 'center' }}
              verticalAlign="bottom"
              iconSize={12}
            />
            <Line 
              type="monotone" 
              dataKey="current" 
              stroke={colors.primary} 
              strokeWidth={2}
              name={period === 'month' ? '이번 달' : '이번 주'}
              dot={{ fill: colors.primary, r: 4 }}
              connectNulls={false}
            />
            <Line 
              type="monotone" 
              dataKey="previous" 
              stroke={colors.secondary} 
              strokeWidth={2}
              name={period === 'month' ? '저번 달' : '저번 주'}
              dot={{ fill: colors.secondary, r: 4 }}
              connectNulls={false}
            />
          </LineChart>
        </ResponsiveContainer>
      ) : (
        <div className="flex items-center justify-center h-64 text-text-muted">
          <p>데이터가 없습니다.</p>
        </div>
      )}
    </div>
  );
}

