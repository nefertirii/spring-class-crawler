package com.example.crawler.coloso;

import com.example.crawler.domain.Lecture;
import com.example.crawler.domain.LectureService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@RequiredArgsConstructor
@Component
public class ColosoCrawler {

    private static final String SOURCE = "coloso";
    private static final String BASE_URL = "https://coloso.co.kr/";
    private static final String CATEGORIES_URL = BASE_URL + "/api/displays";
    private static final String CATEGORY_COURSES_URL = BASE_URL + "/category";
    private static final String COURSE_URL = BASE_URL + "/api/catalogs/courses";
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final LectureService lectureService;

    public void get() {

        log.info("==================================================");
        log.info("Get coloso categories");
        log.info("==================================================");
        List<ColosoCategoryListReadResponse.Category> categories = getCategories();
        log.info("==================================================");
        log.info("Get coloso courses");
        log.info("==================================================");
        List<ColosoCourse> courses = getCourses(categories);
        log.info("==================================================");
        log.info("Convert coloso courses to lectures and save lectures");
        List<Lecture> lectures = convertCourses(courses);
        lectureService.saveOrUpdateLectures(SOURCE, lectures);
    }

    private List<ColosoCategoryListReadResponse.Category> getCategories() {

        ColosoCategoryListReadResponse response = restTemplate.getForObject(CATEGORIES_URL, ColosoCategoryListReadResponse.class);

        if (response == null) {
            log.error("Coloso course categories read failed, {}", CATEGORIES_URL);
            throw new RuntimeException("Coloso course categories read failed");
        }

        List<ColosoCategoryListReadResponse.Category> categories = response.getCategories();

        for (ColosoCategoryListReadResponse.Category category : categories) {
            String mainCategory = category.getTitle();
            List<ColosoCategoryListReadResponse.Category> children = category.getChildren();
            for (ColosoCategoryListReadResponse.Category child : children) {
                String subCategory = child.getTitle();
                log.info("{}, {}", mainCategory, subCategory);
            }
        }

        return categories;
    }

    private List<ColosoCourse> getCourses(List<ColosoCategoryListReadResponse.Category> categories) {

        List<ColosoCourse> colosoCourses = new ArrayList<>();
        for (ColosoCategoryListReadResponse.Category category : categories) {

            for (ColosoCategoryListReadResponse.Category child : category.getChildren()) {

                String mainCategoryTitle = category.getTitle();
                String subCategoryTitle = child.getTitle();
                Long subCategoryId = child.getId();

                log.info("==================================================");
                log.info("Get coloso courses from {}, {}", mainCategoryTitle, subCategoryTitle);
                log.info("==================================================");

                String url = CATEGORY_COURSES_URL + "/" + subCategoryId;

                // 카테고리 강의 목록
                Connection connection = Jsoup.connect(url);
                try {
                    Document document = connection.get();
                    Elements listElements = document.select("section > h3 ~ ul > li");

                    for (Element listElement : listElements) {
                        Element anchorElement = listElement.select("> a").first();
                        if (anchorElement == null) {
                            log.error("Course url read failed, anchor tag does not exists, {}, {}", mainCategoryTitle, subCategoryTitle);
                            continue;
                        }

                        String courseUrl = anchorElement.attr("abs:href");

                        // 강의 상세 조회
                        connection = Jsoup.connect(courseUrl);

                        try {
                            document = connection.get();
                        } catch (Exception exception) {
                            log.error("Course read failed, {}, {}", mainCategoryTitle, subCategoryTitle);
                            continue;
                        }

                        Element scriptElement = document.select("script[type=\"application/ld+json\"]").first();
                        if (scriptElement == null) {
                            log.error("<script type\"application/ld+json\"> does not exists");
                            continue;
                        }

                        String json = scriptElement.data();
                        Product product;

                        try {
                            product = objectMapper.readValue(json, Product.class);
                        } catch (Exception exception) {
                            log.error("application/ld+json parsing failed");
                            continue;
                        }

                        Long id = product.getProductId();

                        List<Product.Offer> offers = product.getOffers();
                        if (offers.isEmpty()) {
                            log.error("Product offer is empty");
                            continue;
                        }

                        Product.Offer offer = offers.get(0);
                        List<Product.Offer.PriceSpecification> priceSpecifications = offer.getPriceSpecifications();
                        if (priceSpecifications.isEmpty()) {
                            log.error("Price specification is empty");
                            continue;
                        }
                        Product.Offer.PriceSpecification priceSpecification = priceSpecifications.get(0);
                        Long price = priceSpecification.getPrice();

                        ColosoCourseReadResponse response = restTemplate.getForObject(COURSE_URL + "?id=" + id.toString(), ColosoCourseReadResponse.class);
                        if (response == null) {
                            log.error("Course read failed");
                            continue;
                        }

                        List<ColosoCourseReadResponse.Course> courses = response.getCourses();
                        if (courses.isEmpty()) {
                            log.error("Price specification is empty");
                            continue;
                        }

                        ColosoCourseReadResponse.Course course = courses.get(0);

                        String title = course.getPublicTitle();
                        String instructor = course.getInstructor();
                        String keywords = course.getKeywords().replaceAll("\\s", " ").replaceAll(" {2,}", " ").trim();
                        String imageUrl = course.getDesktopCardAsset();

                        StringBuilder sb = new StringBuilder();
                        ColosoCourseReadResponse.Course.Extras extras = course.getExtras();
                        if (extras != null) {
                            String text1 = extras.getAdditionalText1();
                            String text2 = extras.getAdditionalText2();
                            String text3 = extras.getAdditionalText3();
                            if (StringUtils.hasText(text1)) {
                                if (StringUtils.hasText(sb.toString())) {
                                    sb.append(" ");
                                }
                                sb.append(text1);
                            }
                            if (StringUtils.hasText(text2)) {
                                if (StringUtils.hasText(sb.toString())) {
                                    sb.append(" ");
                                }
                                sb.append(text2);
                            }
                            if (StringUtils.hasText(text3)) {
                                if (StringUtils.hasText(sb.toString())) {
                                    sb.append(" ");
                                }
                                sb.append(text3);
                            }
                        }

                        String description = "";
                        if (StringUtils.hasText(sb.toString())) {
                            description = sb.toString().replaceAll("\\s", " ").replaceAll(" {2,}", " ").trim();
                            ;
                        }

                        ColosoCourse colosoCourse = new ColosoCourse(id,
                                title,
                                price,
                                description,
                                keywords,
                                instructor,
                                mainCategoryTitle,
                                subCategoryTitle,
                                courseUrl,
                                imageUrl
                        );
                        colosoCourses.add(colosoCourse);
                        log.info("{}", colosoCourse);
                    }

                } catch (Exception exception) {
                    log.error("Category courses read failed, {}, {}", mainCategoryTitle, subCategoryTitle);
                }
            }
        }

        return colosoCourses;
    }

    public List<Lecture> convertCourses(List<ColosoCourse> courses) {

        List<Lecture> lectures = new ArrayList<>();
        // 중복 제거
        Set<Long> sourceIds = new HashSet<>();

        for (ColosoCourse course : courses) {

            Long id = course.getId();
            if (sourceIds.contains(id)) {
                continue;
            } else {
                sourceIds.add(id);
            }

            String title = course.getTitle();
            Long price = course.getPrice();
            String description = course.getDescription();
            String keywords = course.getKeywords();
            String instructor = course.getInstructor();
            String mainCategory = course.getMainCategory();
            String subCategory = course.getSubCategory();
            String url = course.getUrl();
            String imageUrl = course.getImageUrl();

            Optional<Category> convertedCategory = Arrays.stream(Category.values()).filter(category ->
                            category.getOriginalMainCategory().equals(mainCategory) &&
                                    category.getOriginalSubCategory().equals(subCategory))
                    .findFirst();

            if (convertedCategory.isEmpty()) {
                log.error("Category conversion failed! Main Category: {}, Sub Category: {}", mainCategory, subCategory);
                continue;
            }

            String convertedMainCategory = convertedCategory.get().getConvertedMainCategory();
            String convertedSubCategory = convertedCategory.get().getConvertedSubCategory();

            Lecture lecture = new Lecture(title,
                    SOURCE,
                    id.toString(),
                    url,
                    price.toString(),
                    instructor,
                    imageUrl,
                    mainCategory,
                    subCategory,
                    convertedMainCategory,
                    convertedSubCategory,
                    keywords,
                    description);

            lectures.add(lecture);
        }

        return lectures;
    }
}
