import React, { useEffect, useRef } from 'react';

/**
 * 다음(Daum) 주소 검색 모달 컴포넌트
 * 
 * @param {boolean} isOpen - 모달 열림 여부
 * @param {function} onClose - 모달 닫기 콜백
 * @param {function} onSelect - 주소 선택 시 콜백 (zipcode, address1, address2)
 */
const AddressSearchModal = ({ isOpen, onClose, onSelect }) => {
  const isOpeningRef = useRef(false);
  const onCloseRef = useRef(onClose);
  const onSelectRef = useRef(onSelect);

  // onClose와 onSelect를 ref에 저장하여 의존성 문제 해결
  useEffect(() => {
    onCloseRef.current = onClose;
    onSelectRef.current = onSelect;
  }, [onClose, onSelect]);

  useEffect(() => {
    // isOpen이 false이면 실행하지 않음
    if (!isOpen) {
      isOpeningRef.current = false;
      return;
    }

    // 이미 팝업을 여는 중이면 중복 실행 방지
    if (isOpeningRef.current) {
      return;
    }

    // 다음 주소 검색 API가 로드되었는지 확인
    if (typeof window === 'undefined' || !window.daum || !window.daum.Postcode) {
      console.error('다음 주소 검색 API가 로드되지 않았습니다.');
      alert('주소 검색 서비스를 사용할 수 없습니다. 페이지를 새로고침해주세요.');
      isOpeningRef.current = false;
      onCloseRef.current();
      return;
    }

    // 팝업 열기 시작
    isOpeningRef.current = true;

    // 주소 검색 팝업 열기
    const postcodeInstance = new window.daum.Postcode({
      oncomplete: function(data) {
        // 팝업에서 검색결과 항목을 클릭했을때 실행할 코드
        let addr = ''; // 주소 변수
        let extraAddr = ''; // 참고항목 변수

        // 사용자가 선택한 주소 타입에 따라 해당 주소 값을 가져온다.
        if (data.userSelectedType === 'R') { // 사용자가 도로명 주소를 선택했을 경우
          addr = data.roadAddress;
        } else { // 사용자가 지번 주소를 선택했을 경우(J)
          addr = data.jibunAddress;
        }

        // 사용자가 선택한 주소가 도로명 타입일때 참고항목을 조합한다.
        if(data.userSelectedType === 'R'){
          // 법정동명이 있을 경우 추가한다. (법정리는 제외)
          // 법정동의 경우 마지막 문자가 "동/로/가"로 끝난다.
          if(data.bname !== '' && /[동|로|가]$/g.test(data.bname)){
            extraAddr += data.bname;
          }
          // 건물명이 있고, 공동주택일 경우 추가한다.
          if(data.buildingName !== '' && data.apartment === 'Y'){
            extraAddr += (extraAddr !== '' ? ', ' + data.buildingName : data.buildingName);
          }
          // 표시할 참고항목이 있을 경우, 괄호까지 추가한 최종 문자열을 만든다.
          if(extraAddr !== ''){
            extraAddr = ' (' + extraAddr + ')';
          }
        }

        // 우편번호와 주소 정보를 콜백으로 전달
        isOpeningRef.current = false;
        onSelectRef.current({
          zipcode: data.zonecode,
          address1: addr + (extraAddr !== '' ? extraAddr : ''),
          address2: '' // 상세주소는 사용자가 직접 입력
        });

        // 모달 닫기
        onCloseRef.current();
      },
      onclose: function(state) {
        // 팝업이 닫힐 때 호출 (X 버튼 클릭 또는 ESC 키 등)
        // state가 'COMPLETE_CLOSE'이면 주소 선택 완료, 'FORCE_CLOSE'이면 강제 닫기
        isOpeningRef.current = false;
        onCloseRef.current();
      },
      onresize: function(size) {
        // 팝업 크기 조정 시 호출
      },
      width: '100%',
      height: '100%'
    });

    postcodeInstance.open({
      q: '', // 검색어 (빈 문자열이면 전체 목록)
      left: window.screen.width / 2 - 300, // 팝업 위치 (중앙)
      top: window.screen.height / 2 - 300
    });

    // cleanup: 컴포넌트가 언마운트되거나 isOpen이 false가 되면 실행
    return () => {
      // isOpen이 false로 변경될 때만 리셋 (팝업이 열려있는 중에는 리셋하지 않음)
      if (!isOpen) {
        isOpeningRef.current = false;
      }
    };
  }, [isOpen]); // isOpen이 변경될 때만 실행

  // 모달이 열려있을 때만 팝업 열기 (UI는 렌더링하지 않음)
  if (!isOpen) return null;

  // UI 없이 바로 팝업만 열기
  return null;
};

export default AddressSearchModal;

