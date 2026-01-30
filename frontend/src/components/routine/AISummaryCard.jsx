export default function AISummaryCard({ routine }) {
  // 실제 AI summary가 있으면 사용, 없으면 기본 메시지
  const summaryText = routine?.summary || 'AI 코칭 요약이 없습니다.';
  
  // summary를 줄바꿈 기준으로 분리
  const summaryPoints = summaryText.split('\n').filter(line => line.trim());

  return (
    <div className="bg-neutral-800 rounded-lg p-6 mb-6">
      <div className="flex items-center gap-3 mb-4">
        <div className="w-8 h-8 flex items-center justify-center">
          <span className="text-xl" style={{ color: '#88ce02' }}>☀️</span>
        </div>
        <h2 className="text-xl font-semibold text-neutral-50">AI 코칭 요약</h2>
      </div>
      {summaryPoints.length > 0 ? (
        <ul className="space-y-2">
          {summaryPoints.map((point, index) => (
            <li key={index} className="text-neutral-300 flex items-start gap-2">
              <span className="mt-1" style={{ color: '#88ce02' }}>•</span>
              <span>{point.trim()}</span>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-neutral-400">AI 코칭 요약이 없습니다.</p>
      )}
    </div>
  );
}


