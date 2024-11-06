package com.mmc.bookduck.domain.user.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class UserGrowth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(updatable = false)
    private Long userGrowthId;

    @ColumnDefault("1")
    private int level;

    @ColumnDefault("0")
    private long cumulativeExp;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="user_id", unique = true, updatable = false)
    @NotNull
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Builder
    public UserGrowth(User user) {
        this.user = user;
        this.level = 1;
        this.cumulativeExp = 0L;
    }

    // 레벨업
    public void incrementLevel() {
        this.level += 1;
    }

    // 경험치 획득
    public boolean gainExp(int exp) {
        int previousLevel = this.level;  // 이전 레벨 저장
        this.cumulativeExp += exp;
        checkLevelUp();
        return this.level > previousLevel;  // 레벨업이 되면 true 반환
    }

    private void checkLevelUp() {
        long expThreshold = calculateExpThresholdForLevel(level);  // 현재 레벨의 기준 경험치 계산
        while (this.cumulativeExp >= expThreshold) {
            incrementLevel();
            expThreshold = calculateExpThresholdForLevel(level);  // 새 레벨 기준 경험치로 업데이트
        }
    }

    public long calculateExpThresholdForLevel(int level) {
        return 100L * level;  // 예시: 각 레벨당 100 * 레벨의 경험치 필요
    }
}
