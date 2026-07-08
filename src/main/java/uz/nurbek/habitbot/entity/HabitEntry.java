package uz.nurbek.habitbot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "habit_entry")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HabitEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Telegram chat id of the user who sent the message
    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    // Original raw text the user typed, kept for debugging / re-parsing later
    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;

    // High level bucket: EXERCISE, STEPS, EXPENSE, READING, CUSTOM ...
    @Column(nullable = false)
    private String category;

    // More specific label: pullup, pushup, transport, audio_reading ...
    @Column(nullable = false)
    private String subtype;

    // Numeric value: 10 (turnik), 1700 (so'm), 9700 (qadam), 14 (minut) ...
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal value;

    // Unit: count, som, steps, minutes ...
    private String unit;

    // The date this entry belongs to (handles "kecha" = yesterday)
    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (entryDate == null) {
            entryDate = LocalDate.now();
        }
    }
}
