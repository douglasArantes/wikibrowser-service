/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.javafxpert.wikibrowser;

import com.javafxpert.wikibrowser.model.claimsresponse.*;
import com.javafxpert.wikibrowser.model.claimssparqlresponse.Bindings;
import com.javafxpert.wikibrowser.model.claimssparqlresponse.ClaimsSparqlResponse;
import com.javafxpert.wikibrowser.model.claimssparqlresponse.Picture;
import com.javafxpert.wikibrowser.model.claimssparqlresponse.Results;
import com.javafxpert.wikibrowser.model.conceptmap.ItemRepository;
import com.javafxpert.wikibrowser.model.conceptmap.ItemServiceImpl;
import com.javafxpert.wikibrowser.model.locator.ItemInfoResponse;
import com.javafxpert.wikibrowser.model.thumbnail.ThumbnailCache;
import com.javafxpert.wikibrowser.model.traversalresponse.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Iterator;
import java.util.Optional;

/**
 * Created by jamesweaver on 10/13/15.
 */
@RestController
public class WikiClaimsController {
  private static String WIKIDATA_ITEM_BASE = "http://www.wikidata.org/entity/";
  private static String WIKIDATA_PROP_BASE = "http://www.wikidata.org/prop/direct/";
  public static String WIKIPEDIA_BASE_TEMPLATE = "https://%s.wikipedia.org/";
  public static String WIKIPEDIA_MOBILE_BASE_TEMPLATE = "https://%s.m.wikipedia.org/";
  private static String WIKIPEDIA_TEMPLATE = "https://%s.wikipedia.org/wiki/";
  public static String WIKIPEDIA_MOBILE_TEMPLATE = "https://%s.m.wikipedia.org/wiki/";
  public static String WIKIPEDIA_COMMONS_THUMBNAIL_BASE = "https://commons.wikimedia.org/wiki/Special:Redirect/file/";

  // TODO: Move to configuration file or service
  public static int THUMBNAIL_WIDTH = 100;
  private static int MAX_VALS_FOR_PROP = 25;

  private Log log = LogFactory.getLog(getClass());

  private final WikiBrowserProperties wikiBrowserProperties;

  private final ItemServiceImpl itemService;

  private ItemRepository itemRepository;

  @Autowired
  public WikiClaimsController(WikiBrowserProperties wikiBrowserProperties, ItemServiceImpl itemService) {
    this.wikiBrowserProperties = wikiBrowserProperties;
    this.itemService = itemService;
    itemRepository = itemService.getItemRepository();
  }

  @RequestMapping(value = "/claims", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Object> renderClaims(@RequestParam(value = "id", defaultValue="Q7259")
                                                                 String itemId,
                                                                 @RequestParam(value = "lang")
                                                                 String lang) {

    String language = wikiBrowserProperties.computeLang(lang);
    ClaimsSparqlResponse claimsSparqlResponse = callClaimsSparqlQuery(itemId, language);
    ClaimsResponse claimsResponse = convertSparqlResponse(claimsSparqlResponse, language, itemId);

    //log.info("claimsResponse:" + claimsResponse);

    return Optional.ofNullable(claimsResponse)
        .map(cr -> new ResponseEntity<>((Object)cr, HttpStatus.OK))
        .orElse(new ResponseEntity<>("Wikidata query unsuccessful", HttpStatus.INTERNAL_SERVER_ERROR));

  }

  @RequestMapping(value = "/claimsxml", method = RequestMethod.GET, produces = MediaType.APPLICATION_XML_VALUE)
  public ResponseEntity<Object> renderClaimsXml(@RequestParam(value = "id", defaultValue="Q7259")
                                                             String itemId,
                                                             @RequestParam(value = "lang")
                                                             String lang) {

    String language = wikiBrowserProperties.computeLang(lang);
    ClaimsSparqlResponse claimsSparqlResponse = callClaimsSparqlQuery(itemId, language);
    ClaimsResponse claimsResponse = convertSparqlResponse(claimsSparqlResponse, language, itemId);

    return Optional.ofNullable(claimsResponse)
        .map(cr -> new ResponseEntity<>((Object)cr, HttpStatus.OK))
        .orElse(new ResponseEntity<>("Wikidata query unsuccessful", HttpStatus.INTERNAL_SERVER_ERROR));

  }

  private ClaimsSparqlResponse callClaimsSparqlQuery(String itemId, String lang) {
    // Here is the SPARQL query that this method invokes (using Q42 as an example).  Note that it also returns a row for Q42
    /*
      PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
       PREFIX wikibase: <http://wikiba.se/ontology#>
       PREFIX entity: <http://www.wikidata.org/entity/>
       PREFIX p: <http://www.wikidata.org/prop/direct/>

       SELECT ?propUrl ?propLabel ?valUrl ?valLabel ?picture
       WHERE {
         hint:Query hint:optimizer 'None' .
         { BIND(entity:Q42 AS ?valUrl) .
           BIND("N/A" AS ?propUrl ) .
           BIND("identity"@en AS ?propLabel ) .
         }
         UNION
         { entity:Q42 ?propUrl ?valUrl .

           ?property ?ref ?propUrl .
           ?property a wikibase:Property .
           ?property rdfs:label ?propLabel
         }

         ?valUrl rdfs:label ?valLabel
         FILTER (LANG(?valLabel) = 'en') .

         OPTIONAL{
           ?valUrl p:P18 ?picture .
         }
         FILTER (lang(?propLabel) = 'en' )
        }
       ORDER BY ?propLabel ?valLabel LIMIT 200
     */

    String wdqa = "https://query.wikidata.org/bigdata/namespace/wdq/sparql?format=json&query=";
    String wdqb = "PREFIX rdfs: %3Chttp://www.w3.org/2000/01/rdf-schema%23%3E ";
    String wdqc = "PREFIX wikibase: %3Chttp://wikiba.se/ontology%23%3E ";
    String wdqd = "PREFIX entity: %3Chttp://www.wikidata.org/entity/%3E ";
    String wdqe = "PREFIX p: %3Chttp://www.wikidata.org/prop/direct/%3E ";
    String wdqf = "SELECT ?propUrl ?propLabel ?valUrl ?valLabel ?picture ";
    String wdqg = "WHERE %7B hint:Query hint:optimizer 'None' . %7B BIND(entity:";
    String wdqh = ""; // Some item ID e.g. Q7259
    String wdqi = " AS ?valUrl) . BIND('N/A' AS ?propUrl ) . BIND('identity'%40";
    String wdqj = ""; // Some language code e.g. en
    String wdqk = " AS ?propLabel ) . %7D UNION %7B entity:";
    String wdql = ""; // Some item ID e.g. Q7259
    String wdqm = " ?propUrl ?valUrl . ?property ?ref ?propUrl . ?property a wikibase:Property . ";
    String wdqn = "?property rdfs:label ?propLabel %7D ?valUrl rdfs:label ?valLabel FILTER (lang(?valLabel) = '";
    String wdqo = ""; // Some language code e.g. en
    String wdqp = "' ) . ";
    String wdqq =   "OPTIONAL %7B ";
    String wdqr =   "  ?valUrl p:P18 ?picture . ";
    String wdqs =   "%7D ";
    String wdqt = "FILTER (lang(?propLabel) = '";
    String wdqu = ""; // Some language code e.g. en
    String wdqv = "' ) ";
    String wdqw = "%7D ORDER BY ?propLabel ?valLabel LIMIT 500";

    ClaimsSparqlResponse claimsSparqlResponse = null;

    wdqh = itemId;
    wdqj = lang;
    wdql = itemId;
    wdqo = lang;
    wdqu = lang;
    String wdQuery = wdqa + wdqb + wdqc + wdqd + wdqe + wdqf + wdqg + wdqh + wdqi + wdqj + wdqk + wdql + wdqm + wdqn +
        wdqo + wdqp + wdqq + wdqr + wdqs + wdqt + wdqu + wdqv + wdqw;
    wdQuery = wdQuery.replaceAll(" ", "%20");
    log.info("wdQuery: " + wdQuery);

    try {

      claimsSparqlResponse = new RestTemplate().getForObject(new URI(wdQuery),
          ClaimsSparqlResponse.class);

      //log.info(claimsSparqlResponse.toString());

    }
    catch (Exception e) {
      e.printStackTrace();
      log.info("Caught exception when calling wikidata sparql query " + e);
    }

    return claimsSparqlResponse;
  }

  private ClaimsResponse convertSparqlResponse(ClaimsSparqlResponse claimsSparqlResponse, String lang, String itemId) {
    ClaimsResponse claimsResponse = new ClaimsResponse();
    claimsResponse.setLang(lang);
    claimsResponse.setWdItem(itemId);
    claimsResponse.setWdItemBase(WIKIDATA_ITEM_BASE);
    claimsResponse.setWdPropBase(WIKIDATA_PROP_BASE);

    ItemInfoResponse itemInfoResponse = null;

    try {
      String url = this.wikiBrowserProperties.getLocatorServiceUrl(itemId, lang);
      itemInfoResponse = new RestTemplate().getForObject(url,
          ItemInfoResponse.class);

      //log.info(itemInfoResponse.toString());
    }
    catch (Exception e) {
      e.printStackTrace();
      log.info("Caught exception when calling /locator?id=" + itemId + " : " + e);
    }

    if (itemInfoResponse != null) {
      claimsResponse.setArticleTitle(itemInfoResponse.getArticleTitle());
      claimsResponse.setArticleId(itemInfoResponse.getItemId());

      // MERGE item into Neo4j graph
      if (claimsResponse.getArticleId() != null && claimsResponse.getArticleTitle() != null) {
        //log.info("====== itemRepository.addItem: " + claimsResponse.getArticleId() + ", " + claimsResponse.getArticleTitle());
        itemRepository.addItem(claimsResponse.getArticleId(), claimsResponse.getArticleTitle());
      }
    }

    //TODO: Consider implementing fallback to "en" if Wikipedia article doesn't exist in requested language
    claimsResponse.setWpBase(String.format(WIKIPEDIA_TEMPLATE, lang));

    //TODO: Consider implementing fallback to "en" if mobile Wikipedia article doesn't exist in requested language
    claimsResponse.setWpMobileBase(String.format(WIKIPEDIA_MOBILE_TEMPLATE, lang));

    Results results = claimsSparqlResponse.getResults();
    Iterator bindingsIter = results.getBindings().iterator();

    String lastPropId = "";
    String lastValId = "";
    int valsForProp = 0;

    WikidataClaim wikidataClaim = null; //TODO: Consider using exception handling to make null assignment unnecessary
    while (bindingsIter.hasNext()) {
      Bindings bindings = (Bindings)bindingsIter.next(); //TODO: Consider renaming Bindings to Binding

      // There is a 1:many relationship between property IDs and related values
      String nextPropUrl = bindings.getPropUrl().getValue();
      String nextPropId = nextPropUrl.substring(nextPropUrl.lastIndexOf("/") + 1);
      String nextValUrl = bindings.getValUrl().getValue();
      String nextValId = nextValUrl.substring(nextValUrl.lastIndexOf("/") + 1);

      // Cache the picture for a thumbnail image
      Picture picture = bindings.getPicture();
      String pictureUrl = "";

      if (picture != null) {
        String pic = bindings.getPicture().getValue();

        // Compute the URL for the thumbnail image
        pictureUrl = computeThumbnailFromSparqlPicture(pic, THUMBNAIL_WIDTH);
        log.info("pictureUrl from claims: " + pictureUrl);

        // Cache the thumbnail image by item ID
        ThumbnailCache.setThumbnailUrlById(nextValId, lang, pictureUrl);
      }

      //log.info("lastPropId: " + lastPropId + ", nextPropId: " + nextPropId);
      if (!nextPropId.equals(lastPropId)) {
        wikidataClaim = new WikidataClaim();
        wikidataClaim.setProp(new WikidataProperty(nextPropId, bindings.getPropLabel().getValue()));
        claimsResponse.getClaims().add(wikidataClaim);

        valsForProp = 0;
      }

      if (((nextPropId.equals(lastPropId) && !nextValId.equals(lastValId)) || !nextPropId.equals(lastPropId)) &&
          valsForProp < MAX_VALS_FOR_PROP) {
        valsForProp++;
        WikidataItem wikidataItem = new WikidataItem(nextValId, bindings.getValLabel().getValue(), pictureUrl);
        wikidataClaim.addItem(wikidataItem);

        // MERGE item and relationships into Neo4j graph
        if (itemId != null &&
            wikidataItem.getId() != null &&
            wikidataClaim.getProp().getId() != null &&
            wikidataClaim.getProp().getId().length() > 0 &&
            wikidataClaim.getProp().getId().substring(0, 1).equalsIgnoreCase("P") &&
            wikidataClaim.getProp().getLabel() != null) {

          // Write item
          //log.info("++++++ itemRepository.addItem: " + wikidataItem.getId() + ", " + wikidataItem.getLabel());
          itemRepository.addItem(wikidataItem.getId(), wikidataItem.getLabel());

          // Write relationship
          //log.info("------ itemRepository.addRelationship: " + itemId + ", " +
          //                 wikidataItem.getId() + ", " +
          //                 wikidataClaim.getProp().getId() + ", " +
          //                 wikidataClaim.getProp().getLabel());

          itemRepository.addRelationship(itemId,
              wikidataItem.getId(),
              wikidataClaim.getProp().getId(),
              wikidataClaim.getProp().getLabel());
        }
      }

      lastPropId = nextPropId;
      lastValId = nextValId;

    }
    return claimsResponse;
  }

  @RequestMapping(value = "/relatedclaims", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Object> renderRelatedClaims(@RequestParam(value = "id", defaultValue="Q7259")
                                                    String itemId,
                                                    @RequestParam(value = "lang")
                                                    String lang) {

    String language = wikiBrowserProperties.computeLang(lang);
    ClaimsSparqlResponse claimsSparqlResponse = callRelatedClaimsSparqlQuery(itemId, language);
    ClaimsResponse claimsResponse = convertRelatedClaimsSparqlResponse(claimsSparqlResponse, language, itemId);

    //log.info("claimsResponse:" + claimsResponse);

    return Optional.ofNullable(claimsResponse)
        .map(cr -> new ResponseEntity<>((Object)cr, HttpStatus.OK))
        .orElse(new ResponseEntity<>("Wikidata related query unsuccessful", HttpStatus.INTERNAL_SERVER_ERROR));

  }


  private ClaimsSparqlResponse callRelatedClaimsSparqlQuery(String itemId, String lang) {
    // Here is the SPARQL query that this method invokes (using Q42 as an example)
    /*
      PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema%23>
      PREFIX wikibase: <http://wikiba.se/ontology%23>
      PREFIX entity: <http://www.wikidata.org/entity/>
      PREFIX p: <http://www.wikidata.org/prop/direct/>
      SELECT ?propUrl ?propLabel ?valUrl ?valLabel ?picture
      WHERE {
          hint:Query hint:optimizer 'None' .
          ?valUrl ?propUrl entity:Q42 .
          ?valUrl rdfs:label ?valLabel .

          FILTER (LANG(?valLabel) = 'en') .

          ?property ?ref ?propUrl .
          ?property a wikibase:Property .
          ?property rdfs:label ?propLabel

          OPTIONAL{
            ?valUrl p:P18 ?picture .
          }
          FILTER (lang(?propLabel) = 'en' )
      }
      ORDER BY ?propLabel ?valLabel LIMIT 200
    */

    //TODO: Implement better way of creating the query represented by the following variables
    String wdqa = "https://query.wikidata.org/bigdata/namespace/wdq/sparql?format=json&query=";
    String wdqb = "PREFIX%20rdfs:%20%3Chttp://www.w3.org/2000/01/rdf-schema%23%3E%20";
    String wdqc = "PREFIX%20wikibase:%20%3Chttp://wikiba.se/ontology%23%3E%20";
    String wdqd = "PREFIX%20entity:%20%3Chttp://www.wikidata.org/entity/%3E%20";
    String wdqe = "PREFIX%20p:%20%3Chttp://www.wikidata.org/prop/direct/%3E%20";
    String wdqf = "SELECT%20?propUrl%20?propLabel%20?valUrl%20?valLabel%20?picture%20";
    String wdqg = "WHERE%20%7B%20hint:Query%20hint:optimizer%20'None'%20.%20?valUrl%20?propUrl%20entity:";
    String wdqh = ""; // Some item ID e.g. Q7259
    String wdqi = "%20.%20?valUrl%20rdfs:label%20?valLabel%20.%20FILTER%20(LANG(?valLabel)%20=%20'";
    String wdqj = ""; // Some language code e.g. en
    String wdqk = "')%20.%20?property%20?ref%20?propUrl%20.%20?property%20a%20wikibase:Property%20.%20";
    String wdql = "?property%20rdfs:label%20?propLabel%20";
    String wdqm =   "OPTIONAL %7B ";
    String wdqn =   "  ?valUrl p:P18 ?picture . ";
    String wdqo =   "%7D ";
    String wdqp = "FILTER%20(lang(?propLabel)%20=%20'";
    String wdqq = ""; // Some language code e.g. en
    String wdqr = "'%20)%20%7D%20ORDER%20BY%20?propLabel%20?valLabel%20LIMIT%20500";

    ClaimsSparqlResponse claimsSparqlResponse = null;

    wdqh = itemId;
    wdqj = lang;
    wdqq = lang;
    String wdQuery = wdqa + wdqb + wdqc + wdqd + wdqe + wdqf + wdqg + wdqh + wdqi + wdqj + wdqk + wdql + wdqm + wdqn + wdqo + wdqp + wdqq + wdqr;
    wdQuery = wdQuery.replaceAll(" ", "%20");
    log.info("wdQuery: " + wdQuery);

    try {

      claimsSparqlResponse = new RestTemplate().getForObject(new URI(wdQuery),
          ClaimsSparqlResponse.class);

      //log.info(claimsSparqlResponse.toString());

    }
    catch (Exception e) {
      e.printStackTrace();
      log.info("Caught exception when calling related wikidata sparql query " + e);
    }

    return claimsSparqlResponse;
  }

  private ClaimsResponse convertRelatedClaimsSparqlResponse(ClaimsSparqlResponse claimsSparqlResponse, String lang, String itemId) {
    ClaimsResponse claimsResponse = new ClaimsResponse();
    claimsResponse.setLang(lang);
    claimsResponse.setWdItem(itemId);
    claimsResponse.setWdItemBase(WIKIDATA_ITEM_BASE);
    claimsResponse.setWdPropBase(WIKIDATA_PROP_BASE);

    ItemInfoResponse itemInfoResponse = null;

    try {
      String url = this.wikiBrowserProperties.getLocatorServiceUrl(itemId, lang);
      itemInfoResponse = new RestTemplate().getForObject(url,
          ItemInfoResponse.class);

      //log.info(itemInfoResponse.toString());
    }
    catch (Exception e) {
      e.printStackTrace();
      log.info("Caught exception when calling /locator?id=" + itemId + " : " + e);
    }

    if (itemInfoResponse != null) {
      claimsResponse.setArticleTitle(itemInfoResponse.getArticleTitle());
      claimsResponse.setArticleId(itemInfoResponse.getItemId());
    }

    //TODO: Consider implementing fallback to "en" if Wikipedia article doesn't exist in requested language
    claimsResponse.setWpBase(String.format(WIKIPEDIA_TEMPLATE, lang));

    //TODO: Consider implementing fallback to "en" if mobile Wikipedia article doesn't exist in requested language
    claimsResponse.setWpMobileBase(String.format(WIKIPEDIA_MOBILE_TEMPLATE, lang));

    Results results = claimsSparqlResponse.getResults();
    Iterator bindingsIter = results.getBindings().iterator();

    String lastPropId = "";
    int valsForProp = 0;

    WikidataClaim wikidataClaim = null; //TODO: Consider using exception handling to make null assignment unnecessary
    while (bindingsIter.hasNext()) {
      Bindings bindings = (Bindings)bindingsIter.next(); //TODO: Consider renaming Bindings to Binding

      // There is a 1:many relationship between property IDs and related values
      String nextPropUrl = bindings.getPropUrl().getValue();
      String nextPropId = nextPropUrl.substring(nextPropUrl.lastIndexOf("/") + 1);
      String nextValUrl = bindings.getValUrl().getValue();
      String nextValId = nextValUrl.substring(nextValUrl.lastIndexOf("/") + 1);

      // Cache the picture for a thumbnail image
      Picture picture = bindings.getPicture();
      String pictureUrl = "";

      if (picture != null) {
        String pic = bindings.getPicture().getValue();

        // Compute the URL for the thumbnail image
        pictureUrl = computeThumbnailFromSparqlPicture(pic, THUMBNAIL_WIDTH);
        log.info("pictureUrl from RELATEDclaims: " + pictureUrl);

        // Cache the thumbnail image by item ID
        ThumbnailCache.setThumbnailUrlById(nextValId, lang, pictureUrl);
      }

      //log.info("lastPropId: " + lastPropId + ", nextPropId: " + nextPropId);
      if (!nextPropId.equals(lastPropId)) {
        wikidataClaim = new WikidataClaim();
        wikidataClaim.setProp(new WikidataProperty(nextPropId, bindings.getPropLabel().getValue()));
        claimsResponse.getClaims().add(wikidataClaim);
        lastPropId = nextPropId;

        valsForProp = 0;
      }

      if (valsForProp < MAX_VALS_FOR_PROP) {
        valsForProp++;
        WikidataItem wikidataItem = new WikidataItem(nextValId, bindings.getValLabel().getValue(), pictureUrl);
        wikidataClaim.addItem(wikidataItem);
      }
    }
    return claimsResponse;
  }

  /**
   * Traverse the given item, returning the results of traversal.
   * TODO: Make constants for depth and limit
   *
   * @param itemId
   * @param propId
   * @param travDirectionArg "f" for forward, "r" for reverse, "u" for undirectied
   * @param depthStr number of levels to traverse, defaults to 200 for now
   * @param targetId specifies the target item when this is used for shortest path calculation
   * @param limitStr maximum number of items to return, defaults to 200 for now
   * @return
   */

  @RequestMapping(value = "/traversal", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Object> traverseItems(@RequestParam(value = "id", defaultValue="Q1")
                                              String itemId,
                                              @RequestParam(value = "prop", defaultValue="P793")
                                              String propId,
                                              @RequestParam(value = "direction", defaultValue="f")
                                              String travDirectionArg,
                                              @RequestParam(value = "depth", defaultValue="200")
                                              String depthStr,
                                              @RequestParam(value = "target", defaultValue="")
                                              String targetId,
                                              @RequestParam(value = "limit", defaultValue="200")
                                              String limitStr,
                                              @RequestParam(value = "lang", defaultValue="en")
                                              String lang) {

    int depthDefault = 200;  //TODO: Perhaps make a constant or configuration parameter
    int limitDefault = 200;  //TODO: Perhaps make a constant or configuration parameter

    int depth;
    try {
      depth = new Integer(depthStr).intValue();
      if (depth <= 0) {
        depth = depthDefault;
      }
    }
    catch (NumberFormatException nfe) {
      depth = 1;
    }

    int limit;
    try {
      limit = new Integer(limitStr).intValue();
      if (limit <= 0) {
        limit = limitDefault;
      }
    }
    catch (NumberFormatException nfe) {
      limit = limitDefault;
    }

    String travDirection;
    if (travDirectionArg.equalsIgnoreCase("r")) {
      travDirection = "Reverse";
    }
    else if (travDirectionArg.equalsIgnoreCase("u")) {
      travDirection = "Undirected";
    }
    else {
      travDirection = "Forward";
    }

    TraversalSparqlResponse traversalSparqlResponse = callTraversalSparqlQuery(itemId, propId, travDirection, depth, targetId, limit, lang);
    TraversalResponse traversalResponse = convertTraversalSparqlResponse(traversalSparqlResponse, lang);

    //log.info("claimsResponse:" + claimsResponse);

    return Optional.ofNullable(traversalResponse)
        .map(cr -> new ResponseEntity<>((Object)cr, HttpStatus.OK))
        .orElse(new ResponseEntity<>("Wikidata traversal query unsuccessful", HttpStatus.INTERNAL_SERVER_ERROR));

  }

  private TraversalSparqlResponse callTraversalSparqlQuery(String itemId, String propId, String travDirection,
                                                       int depth, String targetId, int limit, String lang) {

    // Here is the SPARQL query that this method invokes (using Q1, P793 as an example) for breadth-first traversal
    /*
      PREFIX wd: <http://www.wikidata.org/entity/>
      PREFIX wdt: <http://www.wikidata.org/prop/direct/>
      PREFIX wikibase: <http://wikiba.se/ontology#>
      PREFIX gas: <http://www.bigdata.com/rdf/gas#>

      SELECT ?item ?itemLabel {
        { SERVICE gas:service {
           gas:program gas:gasClass "com.bigdata.rdf.graph.analytics.SSSP";
                       gas:in wd:Q1;
                       gas:traversalDirection "Forward";
                       gas:out ?item;
                       gas:out1 ?depth;
                       gas:maxIterations 3;
                       gas:linkType wdt:P793 .
          }
        }
        SERVICE wikibase:label {bd:serviceParam wikibase:language "en" }
      }
      LIMIT 200
    */

    // Here is the SPARQL query that this method invokes (using Q1, P793,  as an example) for shortest path traversal
    /*
      PREFIX wd: <http://www.wikidata.org/entity/>
      PREFIX wdt: <http://www.wikidata.org/prop/direct/>
      PREFIX wikibase: <http://wikiba.se/ontology#>
      PREFIX gas: <http://www.bigdata.com/rdf/gas#>

      SELECT ?item ?itemLabel ?picture {
        { SERVICE gas:service {
           gas:program gas:gasClass "com.bigdata.rdf.graph.analytics.SSSP" ;
                       gas:in wd:Q40475;
                       gas:target wd:Q3811608;
                       gas:traversalDirection "Undirected";
                       gas:out ?item;
                       gas:out1 ?depth;
                       gas:maxIterations 200;
                       gas:linkType wdt:P161 .
          }
        }
        OPTIONAL{
          ?item wdt:P18 ?picture .
        }
        SERVICE wikibase:label {bd:serviceParam wikibase:language "en" }
      }
      LIMIT 200
    */

    String wdqa = "https://query.wikidata.org/bigdata/namespace/wdq/sparql?format=json&query=";
    String wdqb = "PREFIX wd: %3Chttp://www.wikidata.org/entity/%3E ";
    String wdqc = "PREFIX wdt: %3Chttp://www.wikidata.org/prop/direct/%3E ";
    String wdqd = "PREFIX wikibase: %3Chttp://wikiba.se/ontology%23%3E ";
    String wdqe = "PREFIX gas: %3Chttp://www.bigdata.com/rdf/gas%23%3E ";
    String wdqf = "SELECT ?item ?itemLabel ?picture %7B";
    String wdqg =   "%7B SERVICE gas:service %7B";
    String wdqh =     "gas:program gas:gasClass 'com.bigdata.rdf.graph.analytics.SSSP';";
    String wdqi =       "gas:in wd:" + itemId + ";";
    String wdqj =       targetId.length() > 0 ? "gas:target wd:" + targetId + ";" : "";
    String wdqk =       "gas:traversalDirection '" + travDirection + "';";
    String wdql =       "gas:out ?item;";
    String wdqm =       "gas:out1 ?depth;";
    String wdqn =       "gas:maxIterations " + depth + ";";
    String wdqo =       "gas:linkType wdt:" + propId + " .";
    String wdqp =     "%7D ";
    String wdqq =   "%7D ";
    String wdqr =   "OPTIONAL %7B ";
    String wdqs =   "  ?item wdt:P18 ?picture .";
    String wdqt =   "%7D ";
    String wdqu =   "SERVICE wikibase%3Alabel %7Bbd%3AserviceParam wikibase%3Alanguage %22" + lang + "%22 %7D ";
    String wdqv = "%7D ";
    String wdqw = "LIMIT " + limit;

    TraversalSparqlResponse traversalSparqlResponse = null;

    String wdQuery = wdqa + wdqb + wdqc + wdqd + wdqe + wdqf + wdqg + wdqh + wdqi + wdqj + wdqk + wdql + wdqm + wdqn +
                     wdqo + wdqp + wdqq + wdqr + wdqs + wdqt + wdqu + wdqv + wdqw;
    wdQuery = wdQuery.replaceAll(" ", "%20");
    log.info("wdQuery: " + wdQuery);

    try {

      traversalSparqlResponse = new RestTemplate().getForObject(new URI(wdQuery),
          TraversalSparqlResponse.class);

      //log.info(traversalSparqlResponse.toString());

    }
    catch (Exception e) {
      e.printStackTrace();
      log.info("Caught exception when calling traversal wikidata sparql query " + e);
    }

    return traversalSparqlResponse;
  }

  private TraversalResponse convertTraversalSparqlResponse(TraversalSparqlResponse traversalSparqlResponse, String lang) {
    TraversalResponse traversalResponse = new TraversalResponse();

    TraversalResultsFar results = traversalSparqlResponse.getTraversalResultsFar();
    Iterator bindingsIter = results.getTraversalBindingsFarList().iterator();

    while (bindingsIter.hasNext()) {
      TraversalBindingsFar bindings = (TraversalBindingsFar)bindingsIter.next();

      String nextItemUrl = bindings.getItemUrlFar().getValue();
      String nextItemId = nextItemUrl.substring(nextItemUrl.lastIndexOf("/") + 1);

      // Cache the picture for a thumbnail image
      PictureFar pictureFar = bindings.getPictureFar();
      String pictureUrl = "";

      if (pictureFar != null) {
        String picture = bindings.getPictureFar().getValue();
        // Compute the URL for the thumbnail image
        pictureUrl = computeThumbnailFromSparqlPicture(picture, THUMBNAIL_WIDTH);
        log.info("pictureUrl from traversal: " + pictureUrl);

        // Cache the thumbnail image by item ID
        ThumbnailCache.setThumbnailUrlById(nextItemId, lang, pictureUrl);
      }

      WikidataItem wikidataItem = new WikidataItem(nextItemId, bindings.getItemLabelFar().getValue(), pictureUrl);
      traversalResponse.addItem(wikidataItem);

    }
    return traversalResponse;
  }

  /**
   * Computes the URL to a thumbnail image from a SPARQL query
   * @param picture similar to http://commons.wikimedia.org/wiki/Special:FilePath/Python-Foot.png
   * @return URL similar to https://commons.wikimedia.org/wiki/Special:Redirect/file/Python-Foot.png?width=100px
   */
  private String computeThumbnailFromSparqlPicture(String picture, int thumbnailwidth) {
    // Input
    String filename = picture.substring(picture.lastIndexOf("/") + 1);
    String pictureUrl = WIKIPEDIA_COMMONS_THUMBNAIL_BASE + filename + "?width=" + thumbnailwidth + "px";
    return pictureUrl;
  }
}

