package org.example.netty_basecamp.domains.member.application;

import org.example.netty_basecamp.domains.common.service.TimeGenerator;
import org.example.netty_basecamp.domains.member.domain.Members;
import org.example.netty_basecamp.domains.member.domain.MembersCreate;
import org.example.netty_basecamp.domains.member.domain.MembersUpdate;
import org.example.netty_basecamp.domains.member.domain.MemberRepository;

import java.util.List;

public class MemberApplicationService {

    private final MemberRepository memberRepository;
    private final TimeGenerator timeGenerator;

    public MemberApplicationService(MemberRepository memberRepository, TimeGenerator timeGenerator) {
        this.memberRepository = memberRepository;
        this.timeGenerator = timeGenerator;
    }

    public Members create(MembersCreate create) {
        Members members = Members.create(create, timeGenerator.millis());
        return memberRepository.save(members);
    }

    public Members findById(Long id) {
        Members members = memberRepository.findById(id);
        if (members == null) {
            throw new IllegalArgumentException("Member not found: " + id);
        }
        return members;
    }

    public List<Members> findAll() {
        return memberRepository.findAll();
    }

    public Members update(Long id, MembersUpdate update) {
        Members existing = findById(id);
        Members updated = existing.updateMember(update, timeGenerator.millis());
        return memberRepository.save(updated);
    }

    public void delete(Long id) {
        findById(id);
        memberRepository.deleteById(id);
    }
}
