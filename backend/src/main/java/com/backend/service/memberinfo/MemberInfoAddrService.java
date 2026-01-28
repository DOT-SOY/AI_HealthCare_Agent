package com.backend.service.memberinfo;

import com.backend.domain.memberinfo.MemberInfoAddr;
import com.backend.dto.memberinfo.MemberInfoAddrDTO;

import java.util.List;

public interface MemberInfoAddrService {
    MemberInfoAddr create(MemberInfoAddrDTO requestDto);

    MemberInfoAddr update(Long id, MemberInfoAddrDTO requestDto);

    MemberInfoAddr setDefault(Long id);

    void delete(Long id);

    List<MemberInfoAddr> getList(Long memberId);
}


