package org.example.netty_basecamp.domain.common.vo;

public class Inventory {
    private final int totalCount;
    private final int usedCount;

    public Inventory(int usedCount, int totalCount) {
        this.totalCount = totalCount;
        this.usedCount = usedCount;
    }

    public int getTotalCount() { return totalCount; }
    public int getUsedCount() { return usedCount; }

    public static InventoryBuilder builder() { return new InventoryBuilder(); }

    public static class InventoryBuilder {
        private int usedCount;
        private int totalCount;

        InventoryBuilder() {}

        public InventoryBuilder usedCount(int usedCount) { this.usedCount = usedCount; return this; }
        public InventoryBuilder totalCount(int totalCount) { this.totalCount = totalCount; return this; }

        public Inventory build() {
            return new Inventory(usedCount, totalCount);
        }
    }

    public static Inventory createInitial(int totalCount) {
        if (totalCount < 0) throw new IllegalArgumentException("초기 생성 수량은 0보다 작을 수 없습니다.");
        return new Inventory(0, totalCount);
    }

    // 비즈니스 행위: 쿠폰 발급/사용
    public Inventory use() {
        if (!hasAvailableStock()) { // 재고가 없으면(false) 에러
            throw new IllegalStateException("잔여 재고가 없습니다.");
        }
        return new Inventory(this.usedCount + 1, this.totalCount);
    }

    // 관리 행위: 전체 발행량 조절
    public Inventory addQuantity(int addedCount) {
        if (addedCount < 0) throw new IllegalArgumentException("추가 수량은 0보다 커야 합니다.");
        return new Inventory(this.usedCount, this.totalCount + addedCount);
    }

    public Inventory removeQuantity(int removedCount) {
        if (removedCount < 0) throw new IllegalArgumentException("삭제 수량은 음수일 수 없습니다.");
        if (getRemainCount() < removedCount) { // 현재 남은 수량보다 더 많이 삭제하려 할 때
            throw new IllegalArgumentException("남은 재고보다 더 많은 수량을 삭제할 수 없습니다.");
        }
        return new Inventory(this.usedCount, this.totalCount - removedCount);
    }

    public int getRemainCount() { return this.totalCount - this.usedCount; }
    public boolean hasAvailableStock() { return getRemainCount() > 0; }
    public boolean isStarted() { return this.usedCount > 0; }
}