package org.example.netty_basecamp.domain;

import org.example.netty_basecamp.domain.common.service.TimeGenerator;
import org.example.netty_basecamp.domain.member.domain.Members;
import org.example.netty_basecamp.domain.member.domain.MembersCreate;
import org.example.netty_basecamp.domain.member.domain.MembersUpdate;
import org.example.netty_basecamp.fake.generator.FakeTimeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MembersTest {

    private TimeGenerator createTimeGenerator;
    private TimeGenerator modifyTimeGenerator;

    @BeforeEach
    public void setup() {
        createTimeGenerator = new FakeTimeGenerator(1000L);
        modifyTimeGenerator = new FakeTimeGenerator(9999L);
    }

    @Test
    @DisplayName("MembersCreate로 Members 생성 시 createdAt과 modifiedAt이 동일하게 설정된다")
    void Members_생성() {
        // given
        MembersCreate membersCreate = MembersCreate.builder()
                .name("홍길동")
                .address("서울시 강남구")
                .age(30)
                .build();

        // when
        Members members = Members.create(membersCreate, createTimeGenerator.millis());

        // then
        assertThat(members.getName()).isEqualTo("홍길동");
        assertThat(members.getAddress()).isEqualTo("서울시 강남구");
        assertThat(members.getAge()).isEqualTo(30);
        assertThat(members.getCreatedAt()).isEqualTo(1000L);
        assertThat(members.getModifiedAt()).isEqualTo(1000L);
        assertThat(members.getId()).isNull();
    }

    @Test
    @DisplayName("updateMember 호출 시 name, address가 변경되고 modifiedAt이 갱신된다")
    void Members_수정() {
        // given
        Members original = Members.create(
                MembersCreate.builder()
                        .name("홍길동")
                        .address("서울시 강남구")
                        .age(30)
                        .build(),
                createTimeGenerator.millis()
        );

        MembersUpdate membersUpdate = MembersUpdate.builder()
                .name("홍길동수정")
                .address("서울시 송파구")
                .build();

        // when
        Members updated = original.updateMember(membersUpdate, modifyTimeGenerator.millis());

        // then
        assertThat(updated.getName()).isEqualTo("홍길동수정");
        assertThat(updated.getAddress()).isEqualTo("서울시 송파구");
        assertThat(updated.getCreatedAt()).isEqualTo(1000L);
        assertThat(updated.getModifiedAt()).isEqualTo(9999L);
    }

    @Test
    @DisplayName("updateMember 호출 시 id와 createdAt은 변경되지 않는다")
    void Members_수정_id랑_createAt는_변하지않음() {

        // given
        Members original = Members.builder()
                .id(1L)
                .name("홍길동")
                .address("서울시 강남구")
                .age(30)
                .createdAt(1000L)
                .modifiedAt(1000L)
                .build();

        MembersUpdate membersUpdate = MembersUpdate.builder()
                .name("홍길동수정")
                .address("서울시 송파구")
                .build();

        // when
        Members updated = original.updateMember(membersUpdate, modifyTimeGenerator.millis());

        // then
        assertThat(updated.getId()).isEqualTo(1L);
        assertThat(updated.getCreatedAt()).isEqualTo(1000L);
        assertThat(updated.getModifiedAt()).isEqualTo(9999L);
    }

    @Test
    @DisplayName("updateMember는 기존 객체를 변경하지 않고 새 객체를 반환한다")
    void updateMember_새객체_반환() {
        // given
        Members original = Members.builder()
                .id(1L)
                .name("홍길동")
                .address("서울시 강남구")
                .age(30)
                .createdAt(1000L)
                .modifiedAt(1000L)
                .build();

        MembersUpdate membersUpdate = MembersUpdate.builder()
                .name("홍길동수정")
                .address("서울시 송파구")
                .build();

        // when
        Members updated = original.updateMember(membersUpdate, createTimeGenerator.millis());

        // then
        assertThat(updated).isNotSameAs(original);
        assertThat(original.getName()).isEqualTo("홍길동");
        assertThat(original.getAddress()).isEqualTo("서울시 강남구");
    }
}