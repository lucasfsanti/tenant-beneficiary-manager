package com.tbm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.tbm.pessoa.dto.PessoaInput;
import com.tbm.pessoa.dto.PessoaResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class PessoaIntegrationTest extends AbstractIntegrationTest {

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(loginAndGetToken(ANA_USERNAME, ANA_PASSWORD));
        return headers;
    }

    @Test
    void createsListsGetsAndUpdatesAPessoa() {
        PessoaInput input = new PessoaInput("Fulano de Tal", "12345678909", null, null);
        ResponseEntity<PessoaResponse> createResponse =
                restTemplate.exchange(
                        "/api/pessoas",
                        org.springframework.http.HttpMethod.POST,
                        entity(input, headers()),
                        PessoaResponse.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = createResponse.getBody().id().toString();

        ResponseEntity<Map> listResponse =
                restTemplate.exchange(
                        "/api/pessoas?nome=Fulano",
                        org.springframework.http.HttpMethod.GET,
                        entity(null, headers()),
                        Map.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((java.util.List) listResponse.getBody().get("content")).isNotEmpty();

        ResponseEntity<PessoaResponse> getResponse =
                restTemplate.exchange(
                        "/api/pessoas/" + id,
                        org.springframework.http.HttpMethod.GET,
                        entity(null, headers()),
                        PessoaResponse.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().nome()).isEqualTo("Fulano de Tal");

        PessoaInput updateInput =
                new PessoaInput("Fulano Editado", "12345678909", null, "fulano@example.com");
        ResponseEntity<PessoaResponse> updateResponse =
                restTemplate.exchange(
                        "/api/pessoas/" + id,
                        org.springframework.http.HttpMethod.PUT,
                        entity(updateInput, headers()),
                        PessoaResponse.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().nome()).isEqualTo("Fulano Editado");
        assertThat(updateResponse.getBody().email()).isEqualTo("fulano@example.com");
    }

    @Test
    void rejectsDuplicateCpfWithConflict() {
        PessoaInput input = new PessoaInput("Ciclano", "98765432100", null, null);
        restTemplate.exchange(
                "/api/pessoas",
                org.springframework.http.HttpMethod.POST,
                entity(input, headers()),
                PessoaResponse.class);

        PessoaInput duplicate = new PessoaInput("Outro Nome", "98765432100", null, null);
        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/pessoas",
                        org.springframework.http.HttpMethod.POST,
                        entity(duplicate, headers()),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void rejectsInvalidCpfWithBadRequest() {
        PessoaInput input = new PessoaInput("Beltrano", "00000000000", null, null);
        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/pessoas",
                        org.springframework.http.HttpMethod.POST,
                        entity(input, headers()),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsMissingNomeWithBadRequest() {
        PessoaInput input = new PessoaInput(null, "11223344517", null, null);
        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/pessoas",
                        org.springframework.http.HttpMethod.POST,
                        entity(input, headers()),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsMissingCpfWithBadRequest() {
        PessoaInput input = new PessoaInput("Sem CPF", null, null, null);
        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/pessoas",
                        org.springframework.http.HttpMethod.POST,
                        entity(input, headers()),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listsPessoasWithoutANameFilter() {
        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/pessoas",
                        org.springframework.http.HttpMethod.GET,
                        entity(null, headers()),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((java.util.List) response.getBody().get("content")).isNotEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void listsPessoasWithABlankNameFilterSameAsUnfiltered() {
        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/pessoas?nome=",
                        org.springframework.http.HttpMethod.GET,
                        entity(null, headers()),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((java.util.List) response.getBody().get("content")).isNotEmpty();
    }

    @Test
    void updatingToAGenuinelyDifferentUnusedCpfSucceeds() {
        PessoaInput input = new PessoaInput("Vai Trocar de CPF", "60216365031", null, null);
        String id =
                restTemplate
                        .exchange(
                                "/api/pessoas",
                                org.springframework.http.HttpMethod.POST,
                                entity(input, headers()),
                                PessoaResponse.class)
                        .getBody()
                        .id()
                        .toString();

        PessoaInput updateInput = new PessoaInput("Vai Trocar de CPF", "78460055027", null, null);
        ResponseEntity<PessoaResponse> response =
                restTemplate.exchange(
                        "/api/pessoas/" + id,
                        org.springframework.http.HttpMethod.PUT,
                        entity(updateInput, headers()),
                        PessoaResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().cpf()).isEqualTo("78460055027");

        restTemplate.exchange(
                "/api/pessoas/" + id, org.springframework.http.HttpMethod.DELETE, entity(null, headers()), Void.class);
    }

    @Test
    void rejectsUpdateToADifferentAlreadyRegisteredCpf() {
        PessoaInput firstInput = new PessoaInput("Titular Um", "39053344705", null, null);
        String firstId =
                restTemplate
                        .exchange(
                                "/api/pessoas",
                                org.springframework.http.HttpMethod.POST,
                                entity(firstInput, headers()),
                                PessoaResponse.class)
                        .getBody()
                        .id()
                        .toString();

        PessoaInput secondInput = new PessoaInput("Titular Dois", "45745233877", null, null);
        String secondId =
                restTemplate
                        .exchange(
                                "/api/pessoas",
                                org.springframework.http.HttpMethod.POST,
                                entity(secondInput, headers()),
                                PessoaResponse.class)
                        .getBody()
                        .id()
                        .toString();

        PessoaInput conflictingUpdate = new PessoaInput("Titular Dois", "39053344705", null, null);
        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/pessoas/" + secondId,
                        org.springframework.http.HttpMethod.PUT,
                        entity(conflictingUpdate, headers()),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Cleanup.
        restTemplate.exchange(
                "/api/pessoas/" + firstId, org.springframework.http.HttpMethod.DELETE, entity(null, headers()), Void.class);
        restTemplate.exchange(
                "/api/pessoas/" + secondId, org.springframework.http.HttpMethod.DELETE, entity(null, headers()), Void.class);
    }

    @Test
    void acceptsOmittedOptionalFields() {
        PessoaInput input = new PessoaInput("Sem Opcionais", "54433221171", null, null);
        ResponseEntity<PessoaResponse> response =
                restTemplate.exchange(
                        "/api/pessoas",
                        org.springframework.http.HttpMethod.POST,
                        entity(input, headers()),
                        PessoaResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().dataNascimento()).isNull();
        assertThat(response.getBody().email()).isNull();
    }
}
