package org.example.netty_basecamp.basic.member.domain;

public record MembersUpdate(String name, String address, int age) {

    public static MembersUpdateBuilder builder() {
        return new MembersUpdateBuilder();
    }

    public static class MembersUpdateBuilder {
        private String name;
        private String address;
        private int age;

        MembersUpdateBuilder() {
        }

        public MembersUpdateBuilder name(String name) {
            this.name = name;
            return this;
        }

        public MembersUpdateBuilder address(String address) {
            this.address = address;
            return this;
        }

        public MembersUpdateBuilder age(int age) {
            this.age = age;
            return this;
        }

        public MembersUpdate build() {
            return new MembersUpdate(name, address, age);
        }
    }
}
