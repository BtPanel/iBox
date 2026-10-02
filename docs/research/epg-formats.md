# EPG Formats for IPTV

Research date: 2026-09-16. This note distinguishes a programme-guide
*schema* (the meaning and structure of programmes) from a file extension,
compression format, HTTP content coding, or playlist metadata.

## Executive conclusion

For a seven-day on-screen guide (three past calendar days, today, and three
future calendar days), XMLTV is the most portable interchange choice for a
whole-guide download. It naturally carries a channel identifier plus each
programme's start, stop, and title, and a provider can place all seven days in
one document. A provider-specific JSON endpoint is also suitable when it can
query a channel and date/range, but JSON itself does not define a universal EPG
schema: the endpoint contract must be named and adapted explicitly. DVB SI EIT
and ATSC PSIP EIT are broadcast transport tables, not drop-in XML/JSON files;
they require a demux/decoder for the relevant MPEG transport stream or ATSC
transport. M3U metadata connects channels, XMLTV, and catch-up playback but is
not a programme-guide schema.

`*.xml.gz` and an HTTP `Content-Encoding: gzip` response are compression or
transport representations, not alternative EPG schemas. After decompression,
the payload still needs XMLTV (or another declared schema) parsing.

## Format landscape

| Format or mechanism | What it actually is | Programme data and seven-day suitability | Interoperability consequence |
| --- | --- | --- | --- |
| XMLTV | XML document format defined by the XMLTV project's DTD. | `<channel id>` identifies a service; `<programme channel start stop>` carries broadcasts, with title and optional rich metadata. One download can contain all required dates, so it is a good fit when the source publishes a sufficient range. | Best common external guide format. Match stable `tvg-id` values to XMLTV channel IDs before attempting display names. |
| JSON EPG API | An API-specific JSON representation, not one shared industry schema. | Can efficiently fetch `{channel, date}` or a date range and usually conveys epoch start/stop and title. It is suitable only after defining the provider's envelope, identifier, time unit/time zone, pagination, and requested range. | Use a small adapter per API. A JSON parser alone is not JSON-EPG support. Tvheadend's documented JSON API is a useful concrete example, not a generic standard. |
| DVB SI EIT | Event Information Table in DVB Service Information, carried in MPEG-2 Transport Stream sections. | EIT schedule tables describe future events; EIT present/following describes the current/next event. The available horizon is broadcaster-controlled, so a seven-day guide is feasible only if the multiplex advertises that schedule range. | Requires TS demultiplexing, PSI/SI section reassembly, DVB descriptor handling, and service-ID mapping. It cannot be parsed as an XMLTV file or an HTTP JSON response. |
| ATSC PSIP EIT | Event Information Table in ATSC Program and System Information Protocol, carried in the broadcast transport. | Supplies event listings associated with virtual channels; the broadcaster determines how much schedule data is transmitted. A seven-day UI depends on that retained/received data, not on the EIT name alone. | Requires ATSC transport/PSIP decoding and virtual-channel/service mapping; it is distinct from DVB EIT and XMLTV. |
| M3U/M3U8 IPTV playlist metadata | A line-oriented playlist plus widely used, ecosystem-specific `#EXTM3U`/`#EXTINF` attributes. | It identifies a channel and can point to XMLTV (`x-tvg-url`) or describe catch-up (`catchup`, `catchup-source`, `catchup-days`). It does **not** enumerate programme start/stop/title rows. | Treat playlist parsing, EPG acquisition, and archive-URL construction as separate responsibilities. Attribute names and meanings are conventions, not a universal M3U standard. |
| Gzip / `xml.gz` / HTTP gzip | RFC 1952 file/member format; HTTP content coding in RFC 9110. | No schedule semantics. It can reduce whole-guide download size, which matters for seven days of many channels. | Detect/decode gzip independently of filename. HTTP stacks may already decode `Content-Encoding: gzip`; a downloaded gzip file may instead be identified by its bytes. |

### XMLTV details

The XMLTV DTD states that programmes are not explicitly grouped by day or
channel: the programme's broadcast time and channel are attributes. Its
example uses `<channel id="channel-one.tv">` and `<programme
start="..." stop="..." channel="channel-one.tv">`; its time description
allows a numeric timestamp with an appended time zone. This makes a single
document able to cover arbitrary past and future ranges, provided the supplier
includes them. XMLTV's optional data (description, category, episode number,
credits, ratings, icon) should be preserved only when the product has a UI/use
case for it; title and absolute start/stop are the minimum for a timeline.

### JSON API details

There is no RFC or XMLTV-equivalent common JSON EPG schema. The Tvheadend
project's own JSON API demonstrates why: `epg/events/grid` returns an envelope
with `entries`, per-event `channelUuid`/`channelName`, Unix-second `start` and
`stop`, `title`, and optional fields, with filtering and pagination parameters.
That contract is useful and representative of backend APIs, but it belongs to
Tvheadend. Other JSON providers may use date strings, millisecond timestamps,
different identifiers, or one response per channel/day.

For a seven-day JSON integration, request or retain exactly the inclusive
calendar interval `[today - 3 days, today + 3 days]` in the guide's configured
time zone. Store canonical instants internally and group for UI only after
conversion to that zone. Do not infer a date from a time-only string unless the
API contract supplies the date.

### DVB SI and ATSC PSIP

These are appropriate when the app receives a broadcast transport stream and
needs to extract its in-band guide. They are not normally fetched as a static
IPTV EPG subscription. A practical IPTV application commonly receives a
server-normalized XMLTV/JSON guide instead; adding EIT support is a separate
ingest pipeline, not an additional `EpgParser` input branch.

In DVB SI, EIT table IDs distinguish present/following (`0x4e`/`0x4f`) from
schedule (`0x50`--`0x6f`) tables. Events are scoped to a service, transport
stream, and original network; the binary records carry an event ID, UTC/MJD
start time, BCD duration, flags, and descriptors. The short-event descriptor
contains a language, name, and short text; extended-event descriptors carry
additional multilingual text. In ATSC PSIP, EIT events are associated with the
VCT virtual-channel `source_id`, carry a GPS-based start time, duration, title,
and descriptors; extended text is in the related ETT. A normalizer therefore
needs source IDs and native time-basis conversion before it can create the
same canonical channel/event records used by XMLTV or JSON.

### Playlist and catch-up metadata

Kodi's IPTV Simple project documents a widely encountered convention:
`x-tvg-url` identifies an XMLTV location; `tvg-id` is used to map a playlist
channel to XMLTV; and `catchup`, `catchup-source`, and `catchup-days` determine
how a provider's archived stream URL is built. Its documentation explicitly
labels these as supported M3U/XMLTV elements, which is useful evidence of
common interoperability practice but not evidence of a standards-body schema.
An EPG entry supplies programme boundaries; it does not by itself grant replay
rights or define an archive URL.

RFC 8216 standardizes HLS playlist syntax, including `#EXTM3U` and `#EXTINF`,
but does not define `tvg-id`, `tvg-name`, `x-tvg-url`, `group-title`, or any
`catchup-*` attributes. Those attributes should therefore be treated as
provider/application extensions with profile-specific semantics.

## Suitability for three past + today + three future days

| Ingest choice | Can deliver the requested seven dates? | Operational recommendation |
| --- | --- | --- |
| Whole XMLTV or XMLTV-in-gzip | Yes, if the provider includes all seven dates. | Fetch/cache a guide with an advertised horizon of at least seven dates. Parse all returned dates, but render/query only the product window. |
| Per-channel JSON | Yes, if the endpoint accepts a date/range and actually exposes both past and future listings. | Make seven bounded requests only when the API is date-based, or make one range request. Cache per channel/date and use the source timezone. |
| DVB EIT / ATSC EIT | Maybe. | Validate the transmitted schedule horizon on target services first. Persist decoded events if the UI needs historical entries after a channel/multiplex change. |
| Playlist attributes only | No. | Use them for lookup and catch-up URL templates, alongside a real guide source. |

## Current repository support audit

This is an implementation reading of the current working tree, not a claim
about every IPTV format in the ecosystem.

| Input/feature | Current behavior | Boundaries and gaps |
| --- | --- | --- |
| XMLTV document | Supported. `EpgParser` uses Simple XML to deserialize a `<tv>` root, `channel` elements, `programme` elements, `start`, `stop`, `channel`, first `title`, `display-name`, and channel `icon src`. It associates entries with live channels by exact/normalized `tvg-id`, then `tvg-name`, channel name, or display name. | It does not retain XMLTV descriptions, categories, episode/credit/rating metadata, programme icons, or arbitrary XMLTV extensions. Its concrete `Tv` model accepts only the listed fields. |
| Gzip-compressed EPG file | Supported by gzip magic bytes, not merely the `.gz` suffix. The parser downloads to cache, detects `1f 8b`, decompresses with `GZIPInputStream`, then parses XML. | The code does not have a separate EPG schema for gzip. HTTP content-coding behavior depends on the HTTP client; the parser's explicit gzip branch operates on the downloaded file bytes. |
| Per-channel JSON EPG | Supported only for the app's compact `Epg` contract: object fields `key`, `date`, and `epg_data`, with each item containing `title`, `start`, and `end`. If the response is not a JSON object, the same path tries to parse it as the compact XML `<tv>` form. | There is no general JSON mapping layer. JSON responses such as Tvheadend's `entries` envelope, Xtream-style responses, or arbitrary provider schemas are not parsed unless an upstream endpoint already emits this app's contract. |
| JSON date requests | The configured EPG URL can use `{id}`, `{name}`, `{epg}`, and `{date}`. `LiveApi` replaces the first three for each channel and replaces `{date}` before requesting a date. An active EPG subscription of type `1` supplies request headers. | The ordinary selected-program fetch asks offsets `-1, 0, +1`; it is not an arbitrary range protocol. No JSON pagination, provider time-zone metadata, content negotiation, retries, or response-schema validation is implemented here. |
| Seven-day guide UI/request range | Mobile explicitly creates and requests `-3..+3`, exactly three prior days, today, and three future days. | Leanback currently calls default `GUIDE_START_OFFSET = -6` through `GUIDE_END_OFFSET = +2`: nine dates, asymmetric. XMLTV parsing accepts every date returned by the source and does not itself trim it; actual XMLTV coverage is provider-defined. |
| Playlist EPG linkage | `LiveParser` reads `tvg-url`/`url-tvg` from `#EXTM3U`, then channel `tvg-id`, `tvg-name`, `tvg-logo`, `tvg-chno`, and `group-title`. | It does not parse DVB EIT or ATSC PSIP from streams. Playlist tags identify/match a guide; they are not programme data. |
| Playlist catch-up linkage | `LiveParser` reads `catchup`, `catchup-days`, `catchup-source`, and `catchup-replace`; `Catchup` substitutes programme start/end template values to form a replay URL. | `catchup-days` is not used to filter or prove EPG availability. Provider-specific catch-up modes outside the implementation's supported template behavior need adaptation/testing. |
| DVB SI EIT / ATSC PSIP EIT | Not supported. No EIT/PSIP table parser, MPEG-TS section demuxer, descriptor decoder, or ATSC virtual-channel mapping was found in the application EPG path. | Adding either is a new transport-ingestion capability, not a configuration tweak. |

## Repository references

- `app/src/main/java/com/media/ibox/api/parser/EpgParser.java`: XMLTV mapping,
  gzip magic detection/decompression, cache/download behavior, and channel
  matching.
- `app/src/main/java/com/media/ibox/bean/Tv.java`: exact XML subset modelled.
- `app/src/main/java/com/media/ibox/api/LiveApi.java`: JSON date requests,
  default XML/JSON sources, and guide offsets.
- `app/src/main/java/com/media/ibox/bean/Epg.java` and
  `app/src/main/java/com/media/ibox/bean/EpgData.java`: accepted compact JSON
  shape and time reconstruction.
- `app/src/main/java/com/media/ibox/api/parser/LiveParser.java` and
  `app/src/main/java/com/media/ibox/bean/Catchup.java`: playlist metadata and
  catch-up URL template handling.
- `app/src/mobile/java/com/media/ibox/ui/activity/LiveActivity.java`: mobile
  `-3..+3` guide request.
- `app/src/leanback/java/com/media/ibox/ui/activity/LiveActivity.java`: TV
  default-offset guide request.

## Primary and first-party sources

1. XMLTV project, [XMLTV DTD](https://github.com/XMLTV/xmltv/blob/master/xmltv.dtd).
   Defines the `<tv>`, `<channel>`, and `<programme>` structure, identifiers,
   programme attributes, optional metadata, and timestamp conventions.
2. ETSI, [EN 300 468 V1.17.1: Digital Video Broadcasting (DVB); Specification
   for Service Information (SI) in DVB systems](https://www.etsi.org/deliver/etsi_en/300400_300499/300468/01.17.01_60/en_300468v011701p.pdf).
   Normative DVB SI specification, including EIT table IDs, event records, and
   descriptors.
3. ATSC, [A/65:2013, Program and System Information Protocol for Terrestrial
   Broadcast and Cable](https://www.atsc.org/atsc-documents/a652013-program-and-system-information-protocol-for-terrestrial-broadcast-and-cable/).
   Official publication page for the normative PSIP specification, whose EIT
   and ETT carry programme-guide event and extended-text data.
4. Tvheadend, [JSON API: EPG](https://docs.tvheadend.org/documentation/development/json-api/api-description/epg.md).
   First-party documentation for one concrete JSON EPG API, including its
   `entries` envelope, fields, filtering, and pagination.
5. Kodi PVR IPTV Simple, [README: M3U, XMLTV, and catch-up
   support](https://github.com/kodi-pvr/pvr.iptvsimple/blob/Omega/README.md).
   First-party project documentation for common playlist attributes,
   `tvg-id`/XMLTV matching, and catch-up attribute semantics.
6. IETF, [RFC 8216 sections 4.3.1.1 and 4.3.2.1: HLS `#EXTM3U` and
   `#EXTINF`](https://www.rfc-editor.org/rfc/rfc8216#section-4.3.1.1).
   Standard HLS playlist tags, included to delimit them from the IPTV-specific
   tags above.
7. IETF, [RFC 1952: GZIP file format specification](https://www.rfc-editor.org/rfc/rfc1952).
   Defines the gzip file/member format.
8. IETF, [RFC 9110 section 8.4.1: HTTP content codings](https://www.rfc-editor.org/rfc/rfc9110#section-8.4.1).
   Defines `Content-Encoding` and identifies `gzip` as an HTTP content coding.
