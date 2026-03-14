package org.example.netty_basecamp.domain.member.domain;

public class Members {

    private final Long id;
    private final String name;
    private final String address;
    private final int age;
    private final Long createdAt;
    private final Long modifiedAt;

    public Members(Long id, String name, String address, int age, Long createdAt, Long modifiedAt) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.age = age;
        this.createdAt = createdAt;
        this.modifiedAt = modifiedAt;
    }

    public static Members create(MembersCreate membersCreate, Long time) {

        return Members.builder()
                .name(membersCreate.name())
                .address(membersCreate.address())
                .age(membersCreate.age())
                .createdAt(time)
                .modifiedAt(time)
                .build();
    }

    public Members updateMember(MembersUpdate membersUpdate, Long time) {

        return Members.builder()
                .id(this.id)
                .name(membersUpdate.name())
                .address(membersUpdate.address())
                .createdAt(this.createdAt)
                .modifiedAt(time)
                .build();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public int getAge() { return age; }
    public Long getCreatedAt() { return createdAt; }
    public Long getModifiedAt() { return modifiedAt; }

    public static MembersBuilder builder() { return new MembersBuilder(); }

    public static class MembersBuilder {
        private Long id;
        private String name;
        private String address;
        private int age;
        private Long createdAt;
        private Long modifiedAt;

        MembersBuilder() {}

        public MembersBuilder id(Long id) { this.id = id; return this; }
        public MembersBuilder name(String name) { this.name = name; return this; }
        public MembersBuilder address(String address) { this.address = address; return this; }
        public MembersBuilder age(int age) { this.age = age; return this; }
        public MembersBuilder createdAt(Long createdAt) { this.createdAt = createdAt; return this; }
        public MembersBuilder modifiedAt(Long modifiedAt) { this.modifiedAt = modifiedAt; return this; }

        public Members build() {
            return new Members(id, name, address, age, createdAt, modifiedAt);
        }
    }

}
