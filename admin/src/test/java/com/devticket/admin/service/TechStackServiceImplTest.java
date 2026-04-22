package com.devticket.admin.service;

import com.devticket.admin.application.service.TechStackServiceImpl;
import com.devticket.admin.domain.model.TechStack;
import com.devticket.admin.domain.repository.TechStackRepository;
import com.devticket.admin.infrastructure.external.client.OpenAiEmbeddingClient;
import com.devticket.admin.infrastructure.persistence.repository.TechStackEsRepository;
import com.devticket.admin.presentation.dto.req.CreateTechStackRequest;
import com.devticket.admin.presentation.dto.req.UpdateTechStackRequest;
import com.devticket.admin.presentation.dto.res.CreateTechStackResponse;
import com.devticket.admin.presentation.dto.res.DeleteTechStackResponse;
import com.devticket.admin.presentation.dto.res.GetTechStackResponse;
import com.devticket.admin.presentation.dto.res.UpdateTechStackResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class TechStackServiceImplTest {

    @Mock
    TechStackEsRepository techStackEsRepository;

    @Mock
    TechStackRepository techStackRepository;

    @Mock
    OpenAiEmbeddingClient openAiEmbeddingClient;

    @InjectMocks
    TechStackServiceImpl techStackService;

    // =============== 1. TechStack 조회 =============== //

    @Test
    @DisplayName("테크스택 전체 조회 - 성공")
    void 테크스택_전체_조회_성공() {
        // given
        TechStack 자바 = TechStack.of(1L, "Java");
        TechStack 스프링 = TechStack.of(2L, "Spring");
        given(techStackRepository.getTechStacks()).willReturn(List.of(자바, 스프링));

        // when
        List<GetTechStackResponse> 결과 = techStackService.getTechStacks();

        // then
        assertThat(결과).hasSize(2);
        assertThat(결과.get(0).name()).isEqualTo("Java");
        assertThat(결과.get(1).name()).isEqualTo("Spring");
    }

    // =============== 2. TechStack 생성 =============== //

    @Test
    @DisplayName("테크스택 생성 - 성공")
    void 테크스택_생성_성공() {
        // given
        String 이름 = "Java";
        float[] 임베딩 = new float[]{0.1f, 0.2f, 0.3f};
        TechStack 저장된_테크스택 = TechStack.of(1L, 이름);

        given(techStackRepository.existsByName(이름)).willReturn(false);
        given(techStackRepository.save(any())).willReturn(저장된_테크스택);
        given(openAiEmbeddingClient.embed(이름)).willReturn(임베딩);

        // when
        CreateTechStackResponse 결과 = techStackService.createTechStack(new CreateTechStackRequest(이름));

        // then
        assertThat(결과.name()).isEqualTo(이름);
        then(techStackEsRepository).should().save(1L, 이름, 임베딩);
    }

    @Test
    @DisplayName("테크스택 생성 - 중복 이름 예외")
    void 테크스택_생성_중복_이름_예외() {
        // given
        String 이름 = "Java";
        given(techStackRepository.existsByName(이름)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> techStackService.createTechStack(new CreateTechStackRequest(이름)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("이미 존재하는 TechStack");
    }

    // =============== 3. TechStack 수정 =============== //

    @Test
    @DisplayName("테크스택 수정 - 성공")
    void 테크스택_수정_성공() {
        // given
        Long 아이디 = 1L;
        String 새이름 = "Kotlin";
        float[] 임베딩 = new float[]{0.4f, 0.5f, 0.6f};
        TechStack 기존_테크스택 = TechStack.of(아이디, "Java");

        given(techStackRepository.findById(아이디)).willReturn(Optional.of(기존_테크스택));
        given(openAiEmbeddingClient.embed(새이름)).willReturn(임베딩);

        // when
        UpdateTechStackResponse 결과 = techStackService.updateTechStack(아이디, new UpdateTechStackRequest(null, 새이름));
        // then
        assertThat(결과.name()).isEqualTo(새이름);
        then(techStackEsRepository).should().update(아이디, 새이름, 임베딩);
    }

    @Test
    @DisplayName("테크스택 수정 - 존재하지 않는 ID 예외")
    void 테크스택_수정_존재하지_않는_ID_예외() {
        // given
        Long 아이디 = 999L;
        given(techStackRepository.findById(아이디)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> techStackService.updateTechStack(아이디, new UpdateTechStackRequest(null, "Kotlin")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("존재하지 않는 TechStack");
    }

    // =============== 4. TechStack 삭제 =============== //

    @Test
    @DisplayName("테크스택 삭제 - 성공")
    void 테크스택_삭제_성공() {
        // given
        Long 아이디 = 1L;
        TechStack 기존_테크스택 = TechStack.of(아이디, "Java");
        given(techStackRepository.findById(아이디)).willReturn(Optional.of(기존_테크스택));

        // when
        DeleteTechStackResponse 결과 = techStackService.deleteTechStack(아이디);

        // then
        assertThat(결과.id()).isEqualTo(아이디);
        then(techStackRepository).should().deleteById(아이디);
        then(techStackEsRepository).should().delete(아이디);
    }

    @Test
    @DisplayName("테크스택 삭제 - 존재하지 않는 ID 예외")
    void 테크스택_삭제_존재하지_않는_ID_예외() {
        // given
        Long 아이디 = 999L;
        given(techStackRepository.findById(아이디)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> techStackService.deleteTechStack(아이디))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("존재하지 않는 TechStack");
    }
}