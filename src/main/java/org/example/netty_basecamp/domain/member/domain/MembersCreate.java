package org.example.netty_basecamp.domain.member.domain;

public record MembersCreate(String name, String address, int age) {

    public static MembersCreateBuilder builder() {
        return new MembersCreateBuilder();
    }

    public static class MembersCreateBuilder {
        private String name;
        private String address;
        private int age;

        MembersCreateBuilder() {
        }

        public MembersCreateBuilder name(String name) {
            this.name = name;
            return this;
        }

        public MembersCreateBuilder address(String address) {
            this.address = address;
            return this;
        }

        public MembersCreateBuilder age(int age) {
            this.age = age;
            return this;
        }

        public MembersCreate build() {
            return new MembersCreate(name, address, age);
        }
    }
}
