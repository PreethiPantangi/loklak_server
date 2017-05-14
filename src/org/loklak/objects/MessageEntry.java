/**
 *  MessageEntry
 *  Copyright 22.02.2015 by Michael Peter Christen, @0rb1t3r
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; wo even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package org.loklak.objects;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;
import org.loklak.api.search.ShortlinkFromTweetServlet;
import org.loklak.data.Classifier;
import org.loklak.data.DAO;
import org.loklak.data.Classifier.Category;
import org.loklak.data.Classifier.Context;
import org.loklak.geo.GeoMark;
import org.loklak.geo.LocationSource;
import org.loklak.objects.QueryEntry.PlaceContext;
import org.loklak.tools.bayes.Classification;

public class MessageEntry extends AbstractObjectEntry implements ObjectEntry {

    public static final String RICH_TEXT_SEPARATOR = "\n***\n";
    
    protected Date timestamp;  // a time stamp that is given in loklak upon the arrival of the tweet which is the current local time
    protected Date created_at; // the time given in the tweet which is the time when the user created it. This is also use to do the index partition into minute, hour, week
    protected Date on;         // on means 'valid from'
    protected Date to;         // 'to' means 'valid_until' and may not be set
    protected SourceType source_type; // where did the message come from
    protected ProviderType provider_type;  // who created the message
    protected String provider_hash, screen_name, retweet_from, id_str, canonical_id, parent, text;
    protected URL status_id_url;
    protected long retweet_count, favourites_count;
    protected LinkedHashSet<String> images, audio, videos;
    protected String place_name, place_id;
    
    // the following fields are either set as a common field or generated by extraction from field 'text' or from field 'place_name'
    protected double[] location_point, location_mark; // coordinate order is [longitude, latitude]
    protected int location_radius; // meter
    protected LocationSource location_source;
    protected PlaceContext place_context;
    protected String place_country;
    private boolean enriched;

    // the following can be computed from the tweet data but is stored in the search index to provide statistical data and ranking attributes
    private int without_l_len, without_lu_len, without_luh_len; // the length of tweets without links, users, hashtags
    private String[] hosts, links, mentions, hashtags; // the arrays of links, users, hashtags
    private Map<Context, Classification<String, Category>> classifier;
    
    public MessageEntry() throws MalformedURLException {
        this.timestamp = new Date();
        this.created_at = new Date();
        this.on = null;
        this.to = null;
        this.source_type = SourceType.GENERIC;
        this.provider_type = ProviderType.NOONE;
        this.provider_hash = "";
        this.screen_name = "";
        this.retweet_from = "";
        this.id_str = "";
        this.canonical_id = "";
        this.parent = "";
        this.text = "";
        this.status_id_url = null;
        this.retweet_count = 0;
        this.favourites_count = 0;
        this.images = new LinkedHashSet<String>();
        this.audio = new LinkedHashSet<String>();
        this.videos = new LinkedHashSet<String>();
        this.place_id = "";
        this.place_name = "";
        this.place_context = null;
        this.place_country = null;
        this.location_point = null;
        this.location_radius = 0;
        this.location_mark = null;
        this.location_source = null;
        this.without_l_len = 0;
        this.without_lu_len = 0;
        this.without_luh_len = 0;
        this.hosts = new String[0];
        this.links = new String[0]; 
        this.mentions = new String[0];
        this.hashtags = new String[0];
        this.classifier = null;
        this.enriched = false;
    }

    public MessageEntry(JSONObject json) {
        Object timestamp_obj = lazyGet(json, AbstractObjectEntry.TIMESTAMP_FIELDNAME); this.timestamp = parseDate(timestamp_obj);
        Object created_at_obj = lazyGet(json, AbstractObjectEntry.CREATED_AT_FIELDNAME); this.created_at = parseDate(created_at_obj);
        Object on_obj = lazyGet(json, "on"); this.on = on_obj == null ? null : parseDate(on);
        Object to_obj = lazyGet(json, "to"); this.to = to_obj == null ? null : parseDate(to);
        String source_type_string = (String) lazyGet(json, "source_type");
        try {
            this.source_type = source_type_string == null ? SourceType.GENERIC : SourceType.byName(source_type_string);
        } catch (IllegalArgumentException e) {
            this.source_type = SourceType.GENERIC;
        }
        String provider_type_string = (String) lazyGet(json, "provider_type");
        if (provider_type_string == null) provider_type_string = ProviderType.NOONE.name();
        try {
            this.provider_type = ProviderType.valueOf(provider_type_string);
        } catch (IllegalArgumentException e) {
            this.provider_type = ProviderType.NOONE;
        }
        this.provider_hash = (String) lazyGet(json, "provider_hash");
        this.screen_name = (String) lazyGet(json, "screen_name");
        this.retweet_from = (String) lazyGet(json, "retweet_from");
        this.id_str = (String) lazyGet(json, "id_str");
        this.text = (String) lazyGet(json, "text");
        try {
            this.status_id_url = new URL((String) lazyGet(json, "link"));
        } catch (MalformedURLException e) {
            this.status_id_url = null;
        }
        this.retweet_count = parseLong((Number) lazyGet(json, "retweet_count"));
        this.favourites_count = parseLong((Number) lazyGet(json, "favourites_count"));
        this.images = parseArrayList(lazyGet(json, "images"));
        this.audio = parseArrayList(lazyGet(json, "audio"));
        this.videos = parseArrayList(lazyGet(json, "videos"));
        this.place_id = parseString((String) lazyGet(json, "place_id"));
        this.place_name = parseString((String) lazyGet(json, "place_name"));
        this.place_country = parseString((String) lazyGet(json, "place_country"));
        if (this.place_country != null && this.place_country.length() != 2) this.place_country = null;

        // optional location
        Object location_point_obj = lazyGet(json, "location_point");
        Object location_radius_obj = lazyGet(json, "location_radius");
        Object location_mark_obj = lazyGet(json, "location_mark");
        Object location_source_obj = lazyGet(json, "location_source");
        if (location_point_obj == null || location_mark_obj == null ||
                !(location_point_obj instanceof List<?>) ||
                !(location_mark_obj instanceof List<?>)) {
            this.location_point = null;
            this.location_radius = 0;
            this.location_mark = null;
            this.location_source = null;
        } else {
            this.location_point = new double[]{(Double) ((List<?>) location_point_obj).get(0), (Double) ((List<?>) location_point_obj).get(1)};
            this.location_radius = (int) parseLong((Number) location_radius_obj);
            this.location_mark = new double[]{(Double) ((List<?>) location_mark_obj).get(0), (Double) ((List<?>) location_mark_obj).get(1)};
            this.location_source = LocationSource.valueOf((String) location_source_obj);
        }
        this.enriched = false;

        // load enriched data
        enrich();
    }
    
    public Date getTimestamp() {
        return this.timestamp == null ? new Date() : this.timestamp;
    }
    
    public Date getCreatedAt() {
        return this.created_at == null ? new Date() : this.created_at;
    }

    public void setCreatedAt(Date created_at) {
        this.created_at = created_at;
    }

    public Date getOn() {
        return this.on;
    }

    public void setOn(Date on) {
        this.on = on;
    }

    public Date getTo() {
        return this.to;
    }

    public void setTo(Date to) {
        this.to = to;
    }
    
    public SourceType getSourceType() {
        return this.source_type;
    }

    public void setSourceType(SourceType source_type) {
        this.source_type = source_type;
    }

    public ProviderType getProviderType() {
        return provider_type;
    }

    public void setProviderType(ProviderType provider_type) {
        this.provider_type = provider_type;
    }

    public String getProviderHash() {
        return provider_hash;
    }

    public void setProviderHash(String provider_hash) {
        this.provider_hash = provider_hash;
    }

    public String getScreenName() {
        return screen_name;
    }

    public void setScreenName(String user_screen_name) {
        this.screen_name = user_screen_name;
    }

    public String getRetweetFrom() {
        return this.retweet_from;
    }
    
    public void setRetweetFrom(String retweet_from) {
        this.retweet_from = retweet_from;
    }
    
    public String getIdStr() {
        return id_str;
    }

    public void setIdStr(String id_str) {
        this.id_str = id_str;
    }

    public URL getStatusIdUrl() {
        return this.status_id_url;
    }

    public void setStatusIdUrl(URL status_id_url) {
        this.status_id_url = status_id_url;
    }

    public long getRetweetCount() {
        return retweet_count;
    }

    public void setRetweetCount(long retweet_count) {
        this.retweet_count = retweet_count;
    }

    public long getFavouritesCount() {
        return this.favourites_count;
    }

    public void setFavouritesCount(long favourites_count) {
        this.favourites_count = favourites_count;
    }

    public String getPlaceName() {
        return place_name;
    }
    
    public PlaceContext getPlaceContext () {
        return place_context;
    }

    public void setPlaceName(String place_name, PlaceContext place_context) {
        this.place_name = place_name;
        this.place_context = place_context;
    }

    public String getPlaceId() {
        return place_id;
    }

    public void setPlaceId(String place_id) {
        this.place_id = place_id;
    }

    /**
     * @return [longitude, latitude]
     */
    public double[] getLocationPoint() {
        return location_point;
    }

    /**
     * set the location
     * @param location_point in the form [longitude, latitude]
     */
    public void setLocationPoint(double[] location_point) {
        this.location_point = location_point;
    }

    /**
     * @return [longitude, latitude] which is inside of getLocationRadius() from getLocationPoint()
     */
    public double[] getLocationMark() {
        return location_mark;
    }

    /**
     * set the location
     * @param location_point in the form [longitude, latitude]
     */
    public void setLocationMark(double[] location_mark) {
        this.location_mark = location_mark;
    }

    /**
     * get the radius in meter
     * @return radius in meter around getLocationPoint() (NOT getLocationMark())
     */
    public int getLocationRadius() {
        return location_radius;
    }

    public void setLocationRadius(int location_radius) {
        this.location_radius = location_radius;
    }

    public LocationSource getLocationSource() {
        return location_source;
    }

    public void setLocationSource(LocationSource location_source) {
        this.location_source = location_source;
    }
    
    public void setText(String text) {
        this.text = text;
    }

    public void setImages(ArrayList<String> images) {
        this.images = parseArrayList(images);
    }

    public void setImages(String[] images) {
        this.images = parseArrayList(images);
    }

    public void setImages(String image) {
        this.images = parseArrayList(image);
    }
    
    public long getId() {
        return Long.parseLong(this.id_str);
    }

    public String[] getHosts() {
        return this.hosts;
    }
    
    public String getText() {
        return this.text;
    }
    
    public TextLinkMap getText(final int iflinkexceedslength, final String urlstub) {
        // check if we shall replace shortlinks
        TextLinkMap tlm = new TextLinkMap();
        tlm.text = this.text;
        String[] links = this.getLinks();
        if (links != null) {
            for (int nth = 0; nth < links.length; nth++) {
                String link = links[nth];
                if (link.length() > iflinkexceedslength) {
                    //if (!DAO.existMessage(this.getIdStr())) break linkloop;
                    String shortlink = urlstub + "/x?id=" + this.getIdStr() +
                            (nth == 0 ? "" : ShortlinkFromTweetServlet.SHORTLINK_COUNTER_SEPERATOR + Integer.toString(nth));
                    if (shortlink.length() < link.length()) {
                        tlm.text = tlm.text.replace(link, shortlink);
                        if (!shortlink.equals(link)) {
                            int stublen = shortlink.length() + 3;
                            if (link.length() >= stublen) link = link.substring(0, shortlink.length()) + "...";
                            tlm.short2long.put(shortlink, link);
                        }
                    }
                }
            }
        }
        return tlm;
    }
    
    public static class TextLinkMap {
        public String text;
        public JSONObject short2long;
        public TextLinkMap() {
            text = "";
            this.short2long = new JSONObject(true);
        }
        public String toString() {
            return this.text;
        }
    }
    
    public int getTextLength() {
        return this.text.length();
    }

    public String[] getMentions() {
        return this.mentions;
    }

    public String[] getHashtags() {
        return this.hashtags;
    }

    public String[] getLinks() {
        return this.links;
    }

    public Collection<String> getImages() {
        return this.images;
    }

    public Classifier.Category getClassifier(Classifier.Context context) {
        if (this.classifier == null) return null;
        Classification<String, Category> classification = this.classifier.get(context);
        if (classification == null) return null;
        return classification.getCategory() == Classifier.Category.NONE ? null : classification.getCategory();
    }
    
    public double getClassifierProbability(Classifier.Context context) {
        if (this.classifier == null) return 0.0d;
        Classification<String, Category> classification = this.classifier.get(context);
        if (classification == null) return 0.0d;
        return classification.getProbability();
    }
    
    final static Pattern SPACEX_PATTERN = Pattern.compile("  +"); // two or more
    final static Pattern URL_PATTERN = Pattern.compile("(?:\\b|^)(https?://[-A-Za-z0-9+&@#/%?=~_()|!:,.;]*[-A-Za-z0-9+&@#/%=~_()|])"); // right boundary must be space or ) since others may appear in urls
    final static Pattern USER_PATTERN = Pattern.compile("(?:[ (]|^)(@..*?)(?:\\b|$)"); // left boundary must be space since the @ is itself a boundary
    final static Pattern HASHTAG_PATTERN = Pattern.compile("(?:[ (]|^)(#..*?)(?:\\b|$)"); // left boundary must be a space since the # is itself a boundary

    /**
     * create enriched data, useful for analytics and ranking:
     * - identify all mentioned users, hashtags and links
     * - count message size without links
     * - count message size without links and without users
     */
    public void enrich() {
        if (this.enriched) return;
        StringBuilder t = new StringBuilder(this.text);

        // extract the links
        List<String> links = extract(t, URL_PATTERN, 1);
        t = new StringBuilder(SPACEX_PATTERN.matcher(t).replaceAll(" ").trim());
        this.without_l_len = t.length(); // len_no_l
        
        // extract the users
        List<String> users = extract(t, USER_PATTERN, 1);
        t = new StringBuilder(SPACEX_PATTERN.matcher(t).replaceAll(" ").trim());
        this.without_lu_len = t.length(); // len_no_l_and_users
        
        // extract the hashtags
        List<String> hashtags = extract(t, HASHTAG_PATTERN, 1);
        t = new StringBuilder(SPACEX_PATTERN.matcher(t).replaceAll(" ").trim());
        this.without_luh_len = t.length(); // len_no_l_and_users_and_hashtags

        // extract the hosts from the links
        Set<String> hosts = new LinkedHashSet<String>();
        for (String u: links) {
            try {
                URL url = new URL(u);
                hosts.add(url.getHost());
            } catch (MalformedURLException e) {}
        }

        this.hosts = new String[hosts.size()];
        int j = 0; for (String host: hosts) this.hosts[j++] = host.toLowerCase();
        
        this.mentions = new String[users.size()];
        for (int i = 0; i < users.size(); i++) this.mentions[i] = users.get(i).substring(1);
        
        this.hashtags = new String[hashtags.size()];
        for (int i = 0; i < hashtags.size(); i++) this.hashtags[i] = hashtags.get(i).substring(1).toLowerCase();

        this.links = new String[links.size()];
        for (int i = 0; i < links.size(); i++) this.links[i] = links.get(i);
        
        // classify content
        this.classifier = Classifier.classify(this.text);
        
        // more media data, analyze the links
        for (String link: this.links) {
            if (link.endsWith(".mp4") || link.endsWith(".m4v") ||
                link.indexOf("vimeo.com") > 0 ||
                link.indexOf("youtube.com") > 0 || link.indexOf("youtu.be") > 0 ||
                link.indexOf("vine.co") > 0 ||
                link.indexOf("ted.com") > 0) {this.videos.add(link); continue;}
            if (link.endsWith(".mp3") ||
                link.indexOf("soundcloud.com") > 0) {this.audio.add(link); continue;}
            if (link.endsWith(".jpg") || 
            	link.endsWith(".jpeg") || 
            	link.endsWith(".png") || 
            	link.endsWith(".gif") ||
            	link.indexOf("flickr.com") > 0 ||
                link.indexOf("instagram.com") > 0 ||
                link.indexOf("imgur.com") > 0 ||
                link.indexOf("giphy.com") > 0 || 
                link.indexOf("pic.twitter.com") > 0) {this.images.add(link); continue;}
        }
        
        // find location
        if ((this.location_point == null || this.location_point.length == 0) && DAO.geoNames != null) {
            GeoMark loc = null;
            if (this.place_name != null && this.place_name.length() > 0 &&
                (this.location_source == null || this.location_source == LocationSource.ANNOTATION || this.location_source == LocationSource.PLACE)) {
                loc = DAO.geoNames.analyse(this.place_name, null, 5, Integer.toString(this.text.hashCode()));
                this.place_context = PlaceContext.FROM;
                this.location_source = LocationSource.PLACE;
            }
            if (loc == null) {
                loc = DAO.geoNames.analyse(this.text, this.hashtags, 5, Integer.toString(this.text.hashCode()));
                this.place_context = PlaceContext.ABOUT;
                this.location_source = LocationSource.ANNOTATION;
            }
            if (loc != null) {
                if (this.place_name == null || this.place_name.length() == 0) this.place_name = loc.getNames().iterator().next();
                this.location_radius = 0;
                this.location_point = new double[]{loc.lon(), loc.lat()}; //[longitude, latitude]
                this.location_mark = new double[]{loc.mlon(), loc.mlat()}; //[longitude, latitude]
                this.place_country = loc.getISO3166cc();
            }
        }
        this.enriched = true;
    }
    
    private static List<String> extract(StringBuilder s, Pattern p, int g) {
        Matcher m = p.matcher(s.toString());
        List<String> l = new ArrayList<String>();
        while (m.find()) l.add(m.group(g));
        for (String r: l) {int i = s.indexOf(r); s.replace(i, i + r.length(), "");}
        return l;
    }
    
    @Override
    public JSONObject toJSON() {
        return toJSON(null, true, Integer.MAX_VALUE, ""); // very important to include calculated data here because that is written into the index using the abstract index factory
    }
    
    public JSONObject toJSON(final UserEntry user, final boolean calculatedData, final int iflinkexceedslength, final String urlstub) {
        JSONObject m = new JSONObject(true);

        // tweet data
        m.put(AbstractObjectEntry.TIMESTAMP_FIELDNAME, utcFormatter.print(getTimestamp().getTime()));
        m.put(AbstractObjectEntry.CREATED_AT_FIELDNAME, utcFormatter.print(getCreatedAt().getTime()));
        if (this.on != null) m.put("on", utcFormatter.print(this.on.getTime()));
        if (this.to != null) m.put("to", utcFormatter.print(this.to.getTime()));
        m.put("screen_name", this.screen_name);
        if (this.retweet_from != null && this.retweet_from.length() > 0) m.put("retweet_from", this.retweet_from);
        TextLinkMap tlm = this.getText(iflinkexceedslength, urlstub);
        m.put("text", tlm); // the tweet; the cleanup is a helper function which cleans mistakes from the past in scraping
        if (this.status_id_url != null) m.put("link", this.status_id_url.toExternalForm());
        m.put("id_str", this.id_str);
        if (this.canonical_id != null) m.put("canonical_id", this.canonical_id);
        if (this.parent != null) m.put("parent", this.parent);
        m.put("source_type", this.source_type.toString());
        m.put("provider_type", this.provider_type.name());
        if (this.provider_hash != null && this.provider_hash.length() > 0) m.put("provider_hash", this.provider_hash);
        m.put("retweet_count", this.retweet_count);
        m.put("favourites_count", this.favourites_count); // there is a slight inconsistency here in the plural naming but thats how it is noted in the twitter api
        m.put("place_name", this.place_name);
        m.put("place_id", this.place_id);
        
        // add statistic/calculated data
        if (calculatedData) {
            
            // text length
            m.put("text_length", this.text.length());
            
            // location data
            if (this.place_context != null) m.put("place_context", this.place_context.name());
            if (this.place_country != null && this.place_country.length() == 2) {
                m.put("place_country", DAO.geoNames.getCountryName(this.place_country));
                m.put("place_country_code", this.place_country);
                m.put("place_country_center", DAO.geoNames.getCountryCenter(this.place_country));
            }
      
            // add optional location data. This is written even if calculatedData == false if the source is from REPORT to prevent that it is lost
            if (this.location_point != null && this.location_point.length == 2 && this.location_mark != null && this.location_mark.length == 2) {
                // reference for this format: https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping-geo-point-type.html#_lat_lon_as_array_5
                m.put("location_point", this.location_point); // [longitude, latitude]
                m.put("location_radius", this.location_radius);
                m.put("location_mark", this.location_mark);
                m.put("location_source", this.location_source.name());
            }
            
            // redundant data for enhanced navigation with aggregations
            m.put("hosts", this.hosts);
            m.put("hosts_count", this.hosts.length);
            m.put("links", this.links);
            m.put("links_count", this.links.length);
            m.put("unshorten", tlm.short2long);
            m.put("images", this.images);
            m.put("images_count", this.images.size());
            m.put("audio", this.audio);
            m.put("audio_count", this.audio.size());
            m.put("videos", this.videos);
            m.put("videos_count", this.videos.size());
            m.put("mentions", this.mentions);
            m.put("mentions_count", this.mentions.length);
            m.put("hashtags", this.hashtags);
            m.put("hashtags_count", this.hashtags.length);
            
            // text classifier
            if (this.classifier != null) {
                for (Map.Entry<Context, Classification<String, Category>> c: this.classifier.entrySet()) {
                    assert c.getValue() != null;
                    if (c.getValue().getCategory() == Classifier.Category.NONE) continue; // we don't store non-existing classifications
                    m.put("classifier_" + c.getKey().name(), c.getValue().getCategory());
                    m.put("classifier_" + c.getKey().name() + "_probability",
                        c.getValue().getProbability() == Float.POSITIVE_INFINITY ? Float.MAX_VALUE : c.getValue().getProbability());
                }
            }
            
            // experimental, for ranking
            m.put("without_l_len", this.without_l_len);
            m.put("without_lu_len", this.without_lu_len);
            m.put("without_luh_len", this.without_luh_len);
        }
        
        // add user
        if (user != null) m.put("user", user.toJSON());
        return m;
    }
    
    public static String html2utf8(String s) {
        int p, q;
        // hex coding &#
        try {
            while ((p = s.indexOf("&#")) >= 0) {
                q = s.indexOf(';', p + 2);
                if (q < p) break;
                String charcode = s.substring(p + 2, q);
                int unicode = s.charAt(0) == 'x' ? Integer.parseInt(charcode.substring(1), 16) : Integer.parseInt(charcode);
                s = s.substring(0, p) + ((unicode == 10 || unicode == 13) ? "\n" : ((char) unicode)) + s.substring(q + 1);
            }
        } catch (Throwable e) {
        	DAO.severe(e);
        }
        // octal coding \\u
        try {
            while ((p = s.indexOf("\\u")) >= 0 && s.length() >= p + 6) {
                char r = ((char) Integer.parseInt(s.substring(p + 2, p + 6), 8));
                if (r < ' ') r = ' ';
                s = s.substring(0, p) + r + s.substring(p + 6);
            }
        } catch (Throwable e) {
        	DAO.severe(e);
        }
        // remove tags
        s = A_END_TAG.matcher(s).replaceAll("");
        s = QUOT_TAG.matcher(s).replaceAll("\"");
        s = AMP_TAG.matcher(s).replaceAll("&");
        // remove funny symbols
        StringBuilder clean = new StringBuilder(s.length() + 5);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (((int) c) == 8232 || c == '\n' || c == '\r') clean.append("\n");
            else if (c < ' ') clean.append(' ');
            else clean.append(c);
        }
        // remove double spaces
        return clean.toString().replaceAll("  ", " ");
    }

    private final static Pattern A_END_TAG = Pattern.compile("</a>");
    private final static Pattern QUOT_TAG = Pattern.compile("&quot;");
    private final static Pattern AMP_TAG = Pattern.compile("&amp;");
}
