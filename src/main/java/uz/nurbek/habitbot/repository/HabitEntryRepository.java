package uz.nurbek.habitbot.repository;

import uz.nurbek.habitbot.entity.HabitEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface HabitEntryRepository extends JpaRepository<HabitEntry, Long> {

    List<HabitEntry> findByChatIdAndEntryDateBetweenOrderByEntryDateAsc(
            Long chatId, LocalDate from, LocalDate to);

    List<HabitEntry> findByChatIdAndEntryDate(Long chatId, LocalDate date);

    @Query("""
           select e.category as category, e.subtype as subtype, sum(e.value) as total, e.unit as unit
           from HabitEntry e
           where e.chatId = :chatId and e.entryDate between :from and :to
           group by e.category, e.subtype, e.unit
           order by e.category, e.subtype
           """)
    List<CategoryTotal> aggregateByRange(@Param("chatId") Long chatId,
                                          @Param("from") LocalDate from,
                                          @Param("to") LocalDate to);

    interface CategoryTotal {
        String getCategory();
        String getSubtype();
        java.math.BigDecimal getTotal();
        String getUnit();
    }

    @Query("select distinct e.chatId from HabitEntry e")
    List<Long> findDistinctChatIds();
}
