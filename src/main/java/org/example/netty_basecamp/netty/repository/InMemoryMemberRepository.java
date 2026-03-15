package org.example.netty_basecamp.netty.repository;

import org.example.netty_basecamp.domain.member.domain.Members;
import org.example.netty_basecamp.domain.member.infrastructure.MemberRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryMemberRepository implements MemberRepository {

    private final Map<Long, Members> store = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1);

    @Override
    public Members findById(Long id) {
        return store.get(id);
    }

    @Override
    public List<Members> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public Members save(Members members) {
        if (members.getId() == null) {
            Long newId = sequence.getAndIncrement();
            Members withId = Members.builder()
                    .id(newId)
                    .name(members.getName())
                    .address(members.getAddress())
                    .age(members.getAge())
                    .createdAt(members.getCreatedAt())
                    .modifiedAt(members.getModifiedAt())
                    .build();
            store.put(newId, withId);
            return withId;
        }
        store.put(members.getId(), members);
        return members;
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }
}
