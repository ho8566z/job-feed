package com.jobfeed.collect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 고용24 공채속보 목록 API를 호출해 공고를 정규화한다.
 * 응답에는 근무지역·직무 필드가 없어 region은 항상 null이다.
 */
@Slf4j
@RequiredArgsConstructor
public class WorknetClient {

    private static final String ROOT_LIST = "dhsOpenEmpInfoList";
    private static final String ROOT_ERROR = "GO24";

    private final RestClient restClient;
    private final WorknetProperties properties;

    /** 한 페이지 결과. total은 키워드에 해당하는 전체 건수 */
    public record Page(int total, List<CollectedJob> items) {
    }

    public Page search(String keyword, int startPage) {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("WORKNET_API_KEY가 설정되지 않았습니다.");
        }
        byte[] xml;
        try {
            // Content-Type에 charset이 없으면 문자열로 받을 때 한글이 깨지므로 바이트로 받아 XML 선언의 인코딩을 따른다.
            xml = restClient.get()
                    .uri(buildUri(keyword, startPage))
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientResponseException e) {
            throw new WorknetApiException(e.getStatusCode().value(),
                    "고용24 API 오류 " + e.getStatusCode().value());
        } catch (RestClientException e) {
            // 메시지에 요청 URL(인증키 포함)이 들어갈 수 있어 클래스 이름만 남긴다.
            throw new WorknetApiException(0, "고용24 호출 실패: " + e.getClass().getSimpleName());
        }
        return parse(xml);
    }

    URI buildUri(String keyword, int startPage) {
        // 값은 템플릿 변수로 넘겨 인증키·한글 키워드의 특수문자(+, /, =)까지 인코딩한다.
        return UriComponentsBuilder.fromUriString(properties.url())
                .queryParam("authKey", "{authKey}")
                .queryParam("callTp", "L")
                .queryParam("returnType", "XML")
                .queryParam("startPage", "{startPage}")
                .queryParam("display", "{display}")
                .queryParam("empWantedTitle", "{keyword}")
                .encode()
                .build(Map.of(
                        "authKey", properties.apiKey(),
                        "startPage", startPage,
                        "display", properties.display(),
                        "keyword", keyword));
    }

    static Page parse(String xml) {
        return parse(xml == null ? null : xml.getBytes(StandardCharsets.UTF_8));
    }

    static Page parse(byte[] xml) {
        if (xml == null || xml.length == 0 || new String(xml, StandardCharsets.UTF_8).isBlank()) {
            throw new WorknetApiException(200, "고용24 응답이 비어 있습니다.");
        }
        Element root = readRoot(xml);
        if (ROOT_ERROR.equals(root.getTagName())) {
            String message = text(root, "error");
            throw new WorknetApiException(200, "고용24 오류 응답: " + (message == null ? "알 수 없음" : message));
        }
        if (!ROOT_LIST.equals(root.getTagName())) {
            throw new WorknetApiException(200, "예상하지 못한 응답 형식: " + root.getTagName());
        }

        int total = parseInt(text(root, "total"));
        List<CollectedJob> items = new ArrayList<>();
        NodeList nodes = root.getElementsByTagName("dhsOpenEmpInfo");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element e = (Element) nodes.item(i);
            String externalId = text(e, "empSeqno");
            String url = text(e, "empWantedHomepgDetail");
            String title = text(e, "empWantedTitle");
            if (externalId == null || url == null || title == null) {
                log.warn("필수 값이 없는 공고를 건너뜁니다. empSeqno={}", externalId);
                continue;
            }
            String company = text(e, "empBusiNm");
            items.add(new CollectedJob(externalId, company == null ? "(회사명 없음)" : company,
                    title, null, parseDate(text(e, "empWantedEndt")), url));
        }
        return new Page(total, items);
    }

    private static Element readRoot(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document doc = factory.newDocumentBuilder().parse(new InputSource(new ByteArrayInputStream(xml)));
            return doc.getDocumentElement();
        } catch (Exception e) {
            throw new WorknetApiException(200, "고용24 응답을 해석하지 못했습니다: " + e.getClass().getSimpleName());
        }
    }

    /** 비어 있으면 null */
    private static String text(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        if (list.getLength() == 0) {
            return null;
        }
        Node node = list.item(0);
        String value = node.getTextContent();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int parseInt(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static LocalDate parseDate(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
