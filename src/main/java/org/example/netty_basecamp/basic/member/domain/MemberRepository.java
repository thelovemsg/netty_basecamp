package org.example.netty_basecamp.basic.member.domain;

import java.util.List;

public interface MemberRepository {
    Members findById(Long id);
    List<Members> findAll();
    Members save(Members members);
    void deleteById(Long id);
}
