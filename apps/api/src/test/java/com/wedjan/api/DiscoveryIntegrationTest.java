package com.wedjan.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedjan.api.auth.JwtService;
import com.wedjan.api.auth.MailService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class DiscoveryIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    @Container @SuppressWarnings("resource") static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){
        registry.add("spring.datasource.url",POSTGRES::getJdbcUrl);registry.add("spring.datasource.username",POSTGRES::getUsername);registry.add("spring.datasource.password",POSTGRES::getPassword);registry.add("spring.data.redis.host",REDIS::getHost);registry.add("spring.data.redis.port",()->REDIS.getMappedPort(6379));
    }
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired JwtService jwt; @Autowired ObjectMapper json;
    @MockitoBean MailService mail;

    @Test void fuzzySearchShowcaseConsentFavoritesShortlistAndSeoWorkEndToEnd() throws Exception {
        UUID owner=account("owner.discovery@example.com","VENDOR");UUID tagged=account("tagged.discovery@example.com","VENDOR");UUID customer=account("customer.discovery@example.com","CUSTOMER");
        UUID category=jdbc.queryForObject("SELECT id FROM categories WHERE slug='wedding-photography'",UUID.class);
        vendor(owner,"Kathmandu Photographer Collective","kathmandu-photographer-collective",category,true);
        vendor(tagged,"Himalayan Floral Studio","himalayan-floral-studio",category,false);
        jdbc.update("INSERT INTO vendor_badges(id,vendor_id,badge) VALUES (?,?,'BUSINESS_VERIFIED')",UUID.randomUUID(),owner);
        UUID[] media={media(owner),media(owner),media(owner)};
        UUID packageId=jdbc.queryForObject("SELECT id FROM packages WHERE vendor_id=?",UUID.class,owner);
        String ownerToken=jwt.generateAccessToken(owner,"owner.discovery@example.com",List.of("VENDOR"));
        String taggedToken=jwt.generateAccessToken(tagged,"tagged.discovery@example.com",List.of("VENDOR"));
        String customerToken=jwt.generateAccessToken(customer,"customer.discovery@example.com",List.of("CUSTOMER"));

        mvc.perform(get("/api/v1/search/vendors").param("q","photografer").param("city","Kathmandu")
                        .param("booking_mode","REQUEST").param("price_max","10000000")
                        .param("guests","100").param("badges","BUSINESS_VERIFIED")
                        .param("lat","27.7172").param("lng","85.324").param("radius","10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].businessName").value("Kathmandu Photographer Collective"))
                .andExpect(jsonPath("$.facets.categories[0].value").value("wedding-photography"));

        String body="""
                {"title":"Monsoon Garden Wedding","eventType":"WEDDING","eventDate":"2026-06-12",
                 "city":"Kathmandu","country":"NP","coverMediaId":"%s","descriptionMd":"A joyful real celebration.",
                 "styleTags":["garden","colourful"],"media":[{"mediaId":"%s","sort":0},{"mediaId":"%s","sort":1},{"mediaId":"%s","sort":2}],
                 "vendorTags":[{"vendorId":"%s","roleLabel":"Florals"}],"status":"PUBLISHED"}
                """.formatted(media[0],media[0],media[1],media[2],tagged);
        MvcResult created=mvc.perform(post("/api/v1/showcases").header("Authorization","Bearer "+ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn();
        JsonNode showcase=read(created);String slug=showcase.get("slug").asText();
        verify(mail,timeout(2000)).sendShowcaseTagRequest(eq("tagged.discovery@example.com"),eq("Monsoon Garden Wedding"),eq("Kathmandu Photographer Collective"),org.mockito.ArgumentMatchers.anyString());
        mvc.perform(get("/api/v1/showcases/{slug}",slug)).andExpect(status().isOk()).andExpect(jsonPath("$.vendorTags.length()").value(0));
        MvcResult pending=mvc.perform(get("/api/v1/showcases/me/pending-tags").header("Authorization","Bearer "+taggedToken)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].vendorTags[0].status").value("PENDING")).andReturn();
        String tagId=read(pending).at("/items/0/vendorTags/0/id").asText();
        mvc.perform(post("/api/v1/showcases/tags/{id}/accept",tagId).header("Authorization","Bearer "+taggedToken)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/showcases/{slug}",slug)).andExpect(status().isOk()).andExpect(jsonPath("$.vendorTags[0].businessName").value("Himalayan Floral Studio"));

        mvc.perform(post("/api/v1/discovery/favorites").header("Authorization","Bearer "+customerToken).contentType(MediaType.APPLICATION_JSON).content("{\"entityType\":\"VENDOR\",\"entityId\":\"%s\"}".formatted(owner))).andExpect(status().isCreated());
        MvcResult list=mvc.perform(post("/api/v1/discovery/shortlists").header("Authorization","Bearer "+customerToken).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Garden wedding\"}")).andExpect(status().isCreated()).andReturn();
        String listId=read(list).get("id").asText();
        mvc.perform(post("/api/v1/discovery/shortlists/{id}/items",listId).header("Authorization","Bearer "+customerToken).contentType(MediaType.APPLICATION_JSON).content("{\"vendorId\":\"%s\",\"packageId\":\"%s\"}".formatted(owner,packageId))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].packageTitle").value("Evergreen photography"));
        mvc.perform(get("/api/v1/seo/np/kathmandu/wedding-photography")).andExpect(status().isOk()).andExpect(jsonPath("$.vendorCount").value(1)).andExpect(jsonPath("$.faqs.length()").value(6));
        Thread.sleep(300);assertThat(jdbc.queryForObject("SELECT count(*) FROM search_events",Long.class)).isGreaterThanOrEqualTo(1);
    }

    private UUID account(String email,String role){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO accounts(id,email,password_hash,status) VALUES (?,?,?,'ACTIVE')",id,email,"test");jdbc.update("INSERT INTO account_roles(id,account_id,role) VALUES (?,?,?)",UUID.randomUUID(),id,role);return id;}
    private void vendor(UUID id,String name,String slug,UUID category,boolean packageRow){jdbc.update("INSERT INTO vendor_profiles(account_id,business_name,slug,tagline,about,base_city,base_country,lat,lng,status,is_public,onboarding_step,currency) VALUES (?,?,?,'Candid wedding stories','Experienced local creative team','Kathmandu','NP',27.7172,85.324,'VERIFIED',true,7,'NPR')",id,name,slug);jdbc.update("INSERT INTO vendor_categories(id,vendor_id,category_id,is_primary) VALUES (?,?,?,true)",UUID.randomUUID(),id,category);jdbc.update("INSERT INTO service_areas(id,vendor_id,mode,city,country,lat,lng,radius_km) VALUES (?,?,'CITY_RADIUS','Kathmandu','NP',27.7172,85.324,80)",UUID.randomUUID(),id);if(packageRow)jdbc.update("INSERT INTO packages(id,vendor_id,category_id,title,slug,description_md,price_cents,currency,pricing_model,min_guests,max_guests,whats_included_md,booking_mode,deposit_pct,cancellation_policy,status) VALUES (?,?,?,'Evergreen photography','evergreen-photography','Wedding photography and candid portraits',8500000,'NPR','FLAT',50,200,'Coverage\\nPortraits\\nGallery','REQUEST',25,'MODERATE','PUBLISHED')",UUID.randomUUID(),id,category);}
    private UUID media(UUID owner){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO media_assets(id,owner_account_id,kind,storage_key,mime,bytes,status) VALUES (?,?,'IMAGE',?,'image/jpeg',1024,'READY')",id,owner,"test/discovery/"+id);return id;}
    private JsonNode read(MvcResult result)throws Exception{return json.readTree(result.getResponse().getContentAsString());}
}
