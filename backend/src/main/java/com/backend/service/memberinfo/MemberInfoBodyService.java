package com.backend.service.memberinfo;

import com.backend.domain.memberinfo.MemberInfoBody;
import com.backend.dto.memberinfo.MemberInfoBodyDTO;
import com.backend.dto.memberinfo.MemberInfoBodyResponseDTO;

import java.util.List;

public interface MemberInfoBodyService {
    MemberInfoBody create(MemberInfoBodyDTO requestDto);

    MemberInfoBody updateHeightWeight(Long id, MemberInfoBodyDTO requestDto);

    void delete(Long id);

    List<MemberInfoBodyResponseDTO> getBodyInfoHistory(Long memberId);

    List<MemberInfoBodyResponseDTO> getBodyInfoHistoryByEmail(String email);
}


