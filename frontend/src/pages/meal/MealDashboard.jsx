import React, { useEffect, useState } from 'react';
import { useNavigate } from "react-router-dom";
import { mealApi } from '../../api/mealApi';
import BasicLayout from '../../components/layout/BasicLayout';

const MealDashboard = () => {
  const navigate = useNavigate();
  const [dashboardData, setDashboardData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchDashboard = async () => {
      try {
        setLoading(true);
        const today = new Date().toISOString().split('T')[0]; // YYYY-MM-DD
        const data = await mealApi.getDashboard(today);
        setDashboardData(data);
      } catch (err) {
        console.error('대시보드 데이터 로드 실패:', err);
        const apiError = err?.response?.data?.error;
        if (apiError === "REQUIRE_LOGIN" || apiError === "UNAUTHORIZED") {
          setError("로그인이 필요합니다. 로그인 페이지로 이동합니다.");
          setTimeout(() => navigate("/member/login"), 700);
        } else {
          setError(err?.message || '데이터를 불러오는데 실패했습니다.');
        }
      } finally {
        setLoading(false);
      }
    };

    fetchDashboard();
  }, []);

  if (loading) {
    return (
      <BasicLayout>
        <div className="w-full bg-baseBg min-h-screen">
          <div className="ui-container py-12">
            <div className="text-center">로딩 중...</div>
          </div>
        </div>
      </BasicLayout>
    );
  }

  if (error) {
    return (
      <BasicLayout>
        <div className="w-full bg-baseBg min-h-screen">
          <div className="ui-container py-12">
            <div className="text-center text-red-500">에러: {error}</div>
          </div>
        </div>
      </BasicLayout>
    );
  }

  return (
    <BasicLayout>
      <div className="w-full bg-baseBg min-h-screen">
        <div className="ui-container py-12 lg:py-16">
          <h1 className="text-2xl font-bold mb-6">식단 대시보드</h1>
          
          {dashboardData && (
            <div className="space-y-6">
              <div className="bg-white p-6 rounded-lg shadow">
                <h2 className="text-xl font-semibold mb-4">날짜: {dashboardData.date}</h2>
                
                {dashboardData.meals && dashboardData.meals.length > 0 ? (
                  <div className="space-y-4">
                    <h3 className="font-semibold">식단 목록</h3>
                    {dashboardData.meals.map((meal, index) => (
                      <div key={meal.scheduleId || index} className="border-b pb-2">
                        <div className="flex justify-between">
                          <span className="font-medium">{meal.mealTime}: {meal.foodName}</span>
                          <span className="text-sm text-gray-600">{meal.calories} kcal</span>
                        </div>
                        {meal.originalFoodName && meal.originalFoodName !== meal.foodName && (
                          <div className="text-xs text-gray-500 mt-1">
                            원래: {meal.originalFoodName} → 현재: {meal.foodName}
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="text-gray-500">등록된 식단이 없습니다.</p>
                )}
              </div>

              {dashboardData.analysisComments && dashboardData.analysisComments.length > 0 && (
                <div className="bg-white p-6 rounded-lg shadow">
                  <h3 className="font-semibold mb-4">분석 코멘트</h3>
                  <ul className="space-y-2">
                    {dashboardData.analysisComments.map((comment, index) => (
                      <li key={index} className="text-sm">{comment}</li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </BasicLayout>
  );
};

export default MealDashboard;
