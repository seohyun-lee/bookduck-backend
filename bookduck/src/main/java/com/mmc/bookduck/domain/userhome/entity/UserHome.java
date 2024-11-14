package com.mmc.bookduck.domain.userhome.entity;

import com.mmc.bookduck.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class UserHome {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(updatable = false)
    private Long userHomeId;

    private LocalDateTime lastModifiedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true, updatable = false)
    @NotNull
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @OneToMany(mappedBy = "userHome", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HomeCard> homeCards = new ArrayList<>();

    @Builder
    public UserHome(User user) {
        this.user = user;
    }

    public void updateLastModifiedAt(LocalDateTime lastModifiedAt) {
        this.lastModifiedAt = lastModifiedAt;
    }

    public void addHomeCard(HomeCard homeCard) {
        homeCards.add(homeCard);
    }

    public void removeHomeCard(HomeCard homeCard) {
        homeCards.remove(homeCard);
        homeCard.setUserHome(null);
    }
}