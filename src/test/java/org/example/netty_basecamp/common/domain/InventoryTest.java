package org.example.netty_basecamp.common.domain;

import org.example.netty_basecamp.basic.common.vo.Inventory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryTest {

    @Test
    @DisplayName("정상적인 수량으로 초기 재고를 생성할 수 있다.")
    void createInitial_Success() {
        // given
        int totalCount = 100;

        // when
        Inventory inventory = Inventory.createInitial(totalCount);

        // then
        assertThat(inventory.getTotalCount()).isEqualTo(totalCount);
        assertThat(inventory.getUsedCount()).isZero();
        assertThat(inventory.getRemainCount()).isEqualTo(totalCount);
        assertThat(inventory.hasAvailableStock()).isTrue();
        assertThat(inventory.isStarted()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, -10})
    @DisplayName("초기 생성 수량이 음수이면 예외가 발생한다.")
    void createInitial_Fail_NegativeCount(int invalidCount) {
        assertThatThrownBy(() -> Inventory.createInitial(invalidCount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("초기 생성 수량은 0보다 작을 수 없습니다.");
    }

    @Test
    @DisplayName("재고를 사용하면 사용된 수량이 1 증가한 새로운 인스턴스를 반환한다.")
    void use_Success() {
        // given
        Inventory inventory = Inventory.createInitial(10);

        // when
        Inventory usedInventory = inventory.use();

        // then
        assertThat(usedInventory.getUsedCount()).isEqualTo(1);
        assertThat(usedInventory.getTotalCount()).isEqualTo(10);
        assertThat(usedInventory.getRemainCount()).isEqualTo(9);
        assertThat(usedInventory.isStarted()).isTrue();

        // 기존 인스턴스의 불변성 검증
        assertThat(inventory.getUsedCount()).isZero();
    }

    @Test
    @DisplayName("잔여 재고가 없을 때 사용을 시도하면 예외가 발생한다.")
    void use_Fail_NoStock() {
        // given
        Inventory inventory = Inventory.createInitial(0);

        // when & then
        assertThatThrownBy(inventory::use)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("잔여 재고가 없습니다.");
    }

    @Test
    @DisplayName("재고의 전체 발행량을 추가할 수 있다.")
    void addQuantity_Success() {
        // given
        Inventory inventory = Inventory.createInitial(10);

        // when
        Inventory addedInventory = inventory.addQuantity(5);

        // then
        assertThat(addedInventory.getTotalCount()).isEqualTo(15);
        assertThat(addedInventory.getRemainCount()).isEqualTo(15);
    }

    @Test
    @DisplayName("음수 수량으로 발행량을 추가하려 하면 예외가 발생한다.")
    void addQuantity_Fail_NegativeCount() {
        // given
        Inventory inventory = Inventory.createInitial(10);

        // when & then
        assertThatThrownBy(() -> inventory.addQuantity(-5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("추가 수량은 0보다 커야 합니다.");
    }

    @Test
    @DisplayName("남은 재고 내에서 전체 발행량을 감소시킬 수 있다.")
    void removeQuantity_Success() {
        // given
        Inventory inventory = Inventory.createInitial(10);

        // when
        Inventory removedInventory = inventory.removeQuantity(3);

        // then
        assertThat(removedInventory.getTotalCount()).isEqualTo(7);
        assertThat(removedInventory.getRemainCount()).isEqualTo(7);
    }

    @Test
    @DisplayName("음수 수량으로 발행량을 감소하려 하면 예외가 발생한다.")
    void removeQuantity_Fail_NegativeCount() {
        // given
        Inventory inventory = Inventory.createInitial(10);

        // when & then
        assertThatThrownBy(() -> inventory.removeQuantity(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("삭제 수량은 음수일 수 없습니다.");
    }

    @Test
    @DisplayName("남은 재고보다 많은 수량을 감소시키려 하면 예외가 발생한다.")
    void removeQuantity_Fail_ExceedRemainCount() {
        // given
        Inventory inventory = Inventory.createInitial(10);
        Inventory usedInventory = inventory.use().use(); // 2개 사용, 남은 재고 8

        // when & then
        assertThatThrownBy(() -> usedInventory.removeQuantity(9))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("남은 재고보다 더 많은 수량을 삭제할 수 없습니다.");
    }
}