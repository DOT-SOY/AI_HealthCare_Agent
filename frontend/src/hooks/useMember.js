import { useState, useEffect } from 'react';
import { memberApi } from '../api/memberApi';

export function useMember() {
  const [member, setMember] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchMember = async () => {
      try {
        setLoading(true);
        const data = await memberApi.getCurrent();
        setMember(data);
      } catch (err) {
        setError(err.message);
        console.error('멤버 정보 조회 실패:', err);
      } finally {
        setLoading(false);
      }
    };

    fetchMember();
  }, []);

  return { member, loading, error };
}


