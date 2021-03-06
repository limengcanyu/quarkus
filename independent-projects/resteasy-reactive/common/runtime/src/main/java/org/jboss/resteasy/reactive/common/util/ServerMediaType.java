package org.jboss.resteasy.reactive.common.util;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.common.http.ServerHttpRequest;

/**
 * A representation of a server side media type.
 *
 */
public class ServerMediaType {

    private final MediaType[] sortedMediaTypes;
    private final MediaType[] sortedOriginalMediaTypes;
    private final MediaType hardCoded;

    public ServerMediaType(String[] mediaTypesStrs, String charset) {
        this(mediaTypesFromArray(mediaTypesStrs), charset);
    }

    private static List<MediaType> mediaTypesFromArray(String[] mediaTypesStrs) {
        List<MediaType> mediaTypes = new ArrayList<>(mediaTypesStrs.length);
        for (String mediaTypesStr : mediaTypesStrs) {
            mediaTypes.add(MediaType.valueOf(mediaTypesStr));
        }
        return mediaTypes;
    }

    public ServerMediaType(List<MediaType> mediaTypes, String charset) {
        this(mediaTypes, charset, false);
    }

    public ServerMediaType(List<MediaType> mediaTypes, String charset, boolean deprioritizeWildcards) {
        if (mediaTypes.isEmpty()) {
            this.sortedOriginalMediaTypes = new MediaType[] { MediaType.WILDCARD_TYPE };
        } else {
            this.sortedOriginalMediaTypes = mediaTypes.toArray(new MediaType[0]);
        }
        sortedMediaTypes = new MediaType[sortedOriginalMediaTypes.length];
        Arrays.sort(sortedOriginalMediaTypes, new Comparator<MediaType>() {
            @Override
            public int compare(MediaType m1, MediaType m2) {
                if (deprioritizeWildcards) {
                    if (m1.isWildcardType() && !m2.isWildcardType()) {
                        return 1;
                    }
                    if (!m1.isWildcardType() && m2.isWildcardType()) {
                        return -1;
                    }
                    if (!m1.isWildcardType() && !m2.isWildcardType()) {
                        if (m1.isWildcardSubtype() && !m2.isWildcardSubtype()) {
                            return 1;
                        }
                        if (!m1.isWildcardSubtype() && m2.isWildcardSubtype()) {
                            return -1;
                        }
                    }
                }

                String qs1s = m1.getParameters().get("qs");
                String qs2s = m2.getParameters().get("qs");
                if (qs1s == null && qs2s == null) {
                    return 0;
                }
                if (qs1s != null) {
                    if (qs2s == null) {
                        return 1;
                    } else {
                        float q1 = Float.parseFloat(qs1s);
                        float q2 = Float.parseFloat(qs2s);
                        return Float.compare(q2, q1);
                    }
                } else {
                    return -1;
                }
            }
        });
        for (int i = 0; i < sortedOriginalMediaTypes.length; ++i) {
            MediaType existing = sortedOriginalMediaTypes[i];
            MediaType m = new MediaType(existing.getType(), existing.getSubtype(), charset);
            sortedMediaTypes[i] = m;
        }
        if (sortedMediaTypes.length == 1
                && !(sortedMediaTypes[0].isWildcardType() || sortedMediaTypes[0].isWildcardSubtype())) {
            hardCoded = sortedMediaTypes[0];
        } else {
            hardCoded = null;
        }
    }

    /**
     *
     * @return An entry containing the negotiated desired media type as a key and the negotiated
     *         provided media type as a value
     */
    public Map.Entry<MediaType, MediaType> negotiateProduces(ServerHttpRequest request) {
        return negotiateProduces(request, this.hardCoded);
    }

    /**
     *
     * @return An entry containing the negotiated desired media type as a key and the negotiated
     *         provided media type as a value
     */
    public Map.Entry<MediaType, MediaType> negotiateProduces(ServerHttpRequest request, MediaType hardCoded) {
        if (hardCoded != null) {
            //technically we should negotiate here, and check if we need to return a 416
            //but for performance reasons we ignore this
            return new AbstractMap.SimpleEntry<>(hardCoded, null);
        }
        String acceptStr = request.getRequestHeader(HttpHeaders.ACCEPT);
        MediaType selectedDesired = null;
        MediaType selectedProvided = null;
        List<MediaType> parsedAccepted;
        if (acceptStr != null) {
            //TODO: this can be optimised
            parsedAccepted = MediaTypeHelper.parseHeader(acceptStr);
            MediaTypeHelper.sortByWeight(parsedAccepted);
            String currentClientQ = null;
            int currentServerIndex = Integer.MAX_VALUE;
            if (!parsedAccepted.isEmpty()) {
                for (MediaType desired : parsedAccepted) {
                    if (selectedDesired != null) {
                        //this is to enable server side q values to take effect
                        //the client side is sorted by q, if we have already picked one and the q is
                        //different then we can return the current one
                        if (!Objects.equals(desired.getParameters().get("q"), currentClientQ)) {
                            if (selectedDesired.equals(MediaType.WILDCARD_TYPE)) {
                                return new AbstractMap.SimpleEntry<>(MediaType.APPLICATION_OCTET_STREAM_TYPE, selectedProvided);
                            }
                            return new AbstractMap.SimpleEntry<>(selectedDesired, selectedProvided);
                        }
                    }
                    for (int j = 0; j < sortedMediaTypes.length; j++) {
                        MediaType provide = sortedMediaTypes[j];
                        if (provide.isCompatible(desired)) {
                            if (selectedDesired == null || j < currentServerIndex) {
                                if (desired.isWildcardType()) {
                                    // this is only preferable if we don't already have a better
                                    // one
                                    if (selectedDesired != null) {
                                        continue;
                                    }
                                    selectedDesired = MediaType.APPLICATION_OCTET_STREAM_TYPE;
                                } else if (desired.isWildcardSubtype()) {
                                    // this is only preferable if we don't already have a better
                                    // one
                                    if (selectedDesired != null) {
                                        continue;
                                    }
                                    // is this even allowed?
                                    if (desired.isCompatible(MediaType.APPLICATION_OCTET_STREAM_TYPE))
                                        selectedDesired = MediaType.APPLICATION_OCTET_STREAM_TYPE;
                                    else
                                        selectedDesired = desired; // keep the subwildcard
                                } else {
                                    selectedDesired = desired;
                                }
                                selectedProvided = provide;
                                currentServerIndex = j;
                                currentClientQ = desired.getParameters().get("q");
                            }
                        }
                    }
                }
            }
        }
        if (selectedDesired == null) {
            selectedDesired = sortedMediaTypes[0];
        }
        if (selectedDesired.equals(MediaType.WILDCARD_TYPE)) {
            return new AbstractMap.SimpleEntry<>(MediaType.APPLICATION_OCTET_STREAM_TYPE, selectedProvided);
        }
        return new AbstractMap.SimpleEntry<>(selectedDesired, selectedProvided);
    }

    public MediaType[] getSortedMediaTypes() {
        return sortedMediaTypes;
    }

    public MediaType[] getSortedOriginalMediaTypes() {
        return sortedOriginalMediaTypes;
    }

}
