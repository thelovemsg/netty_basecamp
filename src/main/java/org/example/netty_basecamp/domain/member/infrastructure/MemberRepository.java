package org.example.netty_basecamp.domain.member.infrastructure;

import org.example.netty_basecamp.domain.member.domain.Members;

import java.util.List;

public interface MemberRepository {
    Members findById(Long id);
    List<Members> findAll();
    Members save(Members members);
    void deleteById(Long id);
}
