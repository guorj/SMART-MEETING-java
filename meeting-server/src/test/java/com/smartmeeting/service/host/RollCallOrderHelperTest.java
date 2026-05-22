package com.smartmeeting.service.host;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RollCallOrderHelperTest {

    record Person(String name) {
    }

    @Test
    @DisplayName("按 preset 顺序重排 DB 无序名单")
    void sortByPresetOrder_reordersToPreset() {
        List<Person> people = new ArrayList<>(List.of(
                new Person("付靖怡"),
                new Person("单承标"),
                new Person("管小慧")));
        List<String> preset = List.of("单承标", "管小慧", "付靖怡", "李海天");

        RollCallOrderHelper.sortByPresetOrder(people, preset, Person::name);

        assertThat(people).extracting(Person::name)
                .containsExactly("单承标", "管小慧", "付靖怡");
    }

    @Test
    @DisplayName("不在 preset 中的参会人排在末尾")
    void sortByPresetOrder_extraAtEnd() {
        List<Person> people = new ArrayList<>(List.of(
                new Person("访客甲"),
                new Person("单承标")));
        List<String> preset = List.of("单承标", "管小慧");

        RollCallOrderHelper.sortByPresetOrder(people, preset, Person::name);

        assertThat(people).extracting(Person::name)
                .containsExactly("单承标", "访客甲");
    }
}
