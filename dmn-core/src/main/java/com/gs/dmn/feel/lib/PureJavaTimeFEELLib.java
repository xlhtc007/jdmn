/**
 * Copyright 2016 Goldman Sachs.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.gs.dmn.feel.lib;

import com.gs.dmn.feel.lib.type.list.DefaultListType;
import com.gs.dmn.feel.lib.type.logic.DefaultBooleanType;
import com.gs.dmn.feel.lib.type.numeric.DefaultNumericType;
import com.gs.dmn.feel.lib.type.string.DefaultStringType;
import com.gs.dmn.feel.lib.type.time.pure.LocalDateType;
import com.gs.dmn.feel.lib.type.time.pure.OffsetTimeType;
import com.gs.dmn.feel.lib.type.time.pure.TemporalAmountDurationType;
import com.gs.dmn.feel.lib.type.time.pure.ZonedDateTimeType;
import com.gs.dmn.runtime.DMNRuntimeException;
import com.gs.dmn.runtime.LambdaExpression;
import com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl;
import net.sf.saxon.xpath.XPathFactoryImpl;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.*;

public class PureJavaTimeFEELLib extends FEELOperators<BigDecimal, LocalDate, OffsetTime, ZonedDateTime, TemporalAmount> implements FEELLib<BigDecimal, LocalDate, OffsetTime, ZonedDateTime, TemporalAmount> {
    protected static final Logger LOGGER = LoggerFactory.getLogger(PureJavaTimeFEELLib.class);

    public PureJavaTimeFEELLib() {
        super(new DefaultNumericType(LOGGER),
                new DefaultBooleanType(LOGGER),
                new DefaultStringType(LOGGER),
                new LocalDateType(LOGGER),
                new OffsetTimeType(LOGGER),
                new ZonedDateTimeType(LOGGER),
                new TemporalAmountDurationType(LOGGER),
                new DefaultListType(LOGGER)
        );
    }

    //
    // Conversion functions
    //

    @Override
    public BigDecimal number(String literal) {
        if (StringUtils.isBlank(literal)) {
            return null;
        }

        try {
            return new BigDecimal(literal, DefaultNumericType.MATH_CONTEXT).stripTrailingZeros();
        } catch (Throwable e) {
            String message = String.format("number(%s)", literal);
            logError(message, e);
            return null;
        }
    }

    @Override
    public BigDecimal number(String from, String groupingSeparator, String decimalSeparator) {
        if (StringUtils.isBlank(from) || groupingSeparator == null || decimalSeparator == null) {
            return null;
        }

        try {
            if (decimalSeparator.equals(".")) {
                decimalSeparator = "\\" + decimalSeparator;
            }
            if (groupingSeparator.equals(".")) {
                groupingSeparator = "\\" + groupingSeparator;
            }
            String[] parts = from.split(decimalSeparator);
            if (parts.length == 1) {
                return number(from.replaceAll(groupingSeparator, ""));
            } else if (parts.length == 2) {
                return number(parts[0].replaceAll(groupingSeparator, "") + "." + parts[1]);
            } else {
                return null;
            }
        } catch (Throwable e) {
            String message = String.format("number(%s, %s, %s)", from, groupingSeparator, decimalSeparator);
            logError(message, e);
            return null;
        }
    }

    @Override
    public String string(Object from) {
        if (from == null) {
            return "null";
        } else if (from instanceof BigDecimal) {
            return ((BigDecimal) from).toPlainString();
        } else if (from instanceof ZonedDateTime) {
            return ((ZonedDateTime) from).format(DateTimeFormatter.ISO_DATE_TIME);
        } else {
            return from.toString();
        }
    }

    @Override
    public LocalDate date(String literal) {
        try {
            if (literal == null) {
                return null;
            }
            return LocalDate.parse(literal, DateTimeFormatter.ISO_DATE);
        } catch (Exception e) {
            String message = String.format("date(%s)", literal);
            logError(message, e);
            return null;
        }
    }

    @Override
    public LocalDate date(BigDecimal year, BigDecimal month, BigDecimal day) {
        return LocalDate.of(year.intValue(), month.intValue(), day.intValue());
    }

    @Override
    public LocalDate date(ZonedDateTime from) {
        if (from == null) {
            return null;
        }
        return from.toLocalDate();
    }
    public LocalDate date(LocalDate from) {
        if (from == null) {
            return null;
        }
        return from;
    }

    @Override
    public OffsetTime time(String literal) {
        if (literal == null) {
            return null;
        }
        try {
            literal = DateTimeUtil.fixDateTimeFormat(literal);
            if (DateTimeUtil.hasZone(literal)) {
                return OffsetTime.parse(literal);
            } if (DateTimeUtil.hasOffset(literal)) {
                return OffsetTime.parse(literal);
            } else {
                return OffsetTime.parse(literal + "Z");
            }
        } catch (Exception e) {
            String message = String.format("time(%s)", literal);
            logError(message, e);
            return null;
        }
    }

    @Override
    public OffsetTime time(BigDecimal hour, BigDecimal minute, BigDecimal second, TemporalAmount offset) {
        if (hour == null || minute == null || second == null || offset == null) {
            return null;
        }

        try {
            long hours = offset.get(ChronoUnit.HOURS);
            long minutes = offset.get(ChronoUnit.MINUTES);
            ZoneOffset zoneOffset = ZoneOffset.ofHoursMinutes((int)hours, (int)minutes);
            return OffsetTime.of(hour.intValue(), minute.intValue(), second.intValue(), 0, zoneOffset);
        } catch (Throwable e) {
            String message = String.format("time(%s, %s, %s, %s)", hour, minute, second, offset);
            logError(message, e);
            return null;
        }
    }

    @Override
    public OffsetTime time(ZonedDateTime from) {
        if (from == null) {
            return null;
        }
        return from.toOffsetDateTime().toOffsetTime();
    }
    public OffsetTime time(OffsetTime from) {
        if (from == null) {
            return null;
        }
        return from;
    }

    @Override
    public ZonedDateTime dateAndTime(String literal) {
        return makeDateTime(literal);
    }

    @Override
    public ZonedDateTime dateAndTime(LocalDate date, OffsetTime time) {
        if (date == null || time == null) {
            return null;
        }

        try {
            time = time.withOffsetSameInstant(ZoneOffset.ofTotalSeconds(0));
            ZonedDateTime zonedDateTime = ZonedDateTime.of(
                    date.getYear(), date.getMonth().getValue(), date.getDayOfMonth(),
                    time.getHour(), time.getMinute(), time.getSecond(), time.getNano(), DateTimeUtil.UTC);
            return zonedDateTime;
        } catch (Throwable e) {
            return null;
        }
    }

    @Override
    public TemporalAmount duration(String from) {
        if (StringUtils.isBlank(from)) {
            return null;
        }

        try {
            throw new DMNRuntimeException("Not supported yet");
        } catch (Throwable e) {
            String message = String.format("duration(%s)", from);
            logError(message, e);
            return null;
        }
    }

    @Override
    public TemporalAmount yearsAndMonthsDuration(ZonedDateTime from, ZonedDateTime to) {
        if (from == null || to == null) {
            return null;
        }

        try {
            return Duration.between(from, to);
        } catch (Throwable e) {
            String message = String.format("yearsAndMonthsDuration(%s, %s)", from, to);
            logError(message, e);
            return null;
        }
    }

    protected ZonedDateTime makeZonedDateTime(String literal) {
        if (StringUtils.isBlank(literal)) {
            return null;
        }

        try {
            return ZonedDateTime.parse(literal, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Throwable e) {
            String message = String.format("makeXMLCalendar(%s)", literal);
            logError(message, e);
            return null;
        }
    }

    private ZonedDateTime makeDateTime(String literal) {
        if (StringUtils.isBlank(literal)) {
            return null;
        }

        try {
            literal = DateTimeUtil.fixDateTimeFormat(literal);
            if (DateTimeUtil.hasZone(literal)) {
                return makeZonedDateTime(literal);
            } else if (DateTimeUtil.hasOffset(literal)) {
                return OffsetDateTime.parse(literal).toZonedDateTime();
            } else {
                return makeZonedDateTime(literal + "Z");
            }
        } catch (Throwable e) {
            String message = String.format("makeDateTime(%s)", literal);
            logError(message, e);
            return null;
        }
    }

    @Override
    public LocalDate toDate(Object object) {
        if (object instanceof ZonedDateTime) {
            return date((ZonedDateTime) object);
        }
        return (LocalDate) object;
    }

    @Override
    public OffsetTime toTime(Object object) {
        if (object instanceof ZonedDateTime) {
            return time((ZonedDateTime) object);
        }
        return (OffsetTime) object;
    }

    //
    // Numeric functions
    //
    @Override
    public BigDecimal decimal(BigDecimal n, BigDecimal scale) {
        if (n == null || scale == null) {
            return null;
        }

        try {
            return n.setScale(scale.intValue(), RoundingMode.HALF_EVEN);
        } catch (Throwable e) {
            String message = String.format("decimal(%s, %s)", n, scale);
            logError(message, e);
            return null;
        }
    }

    @Override
    public BigDecimal floor(BigDecimal number) {
        if (number == null) {
            return null;
        }
        return number.setScale(0, BigDecimal.ROUND_FLOOR);
    }

    @Override
    public BigDecimal ceiling(BigDecimal number) {
        if (number == null) {
            return null;
        }

        try {
            return number.setScale(0, BigDecimal.ROUND_CEILING).stripTrailingZeros();
        } catch (Throwable e) {
            String message = String.format("ceiling(%s)", number);
            logError(message, e);
            return null;
        }
    }

    @Override
    public BigDecimal min(Object... args) {
        if (args == null || args.length < 1) {
            return null;
        }

        try {
            return min(Arrays.asList(args));
        } catch (Throwable e) {
            String message = String.format("min(%s)", args);
            logError(message, e);
            return null;
        }
    }

    @Override
    public BigDecimal max(Object... args) {
        if (args == null || args.length < 1) {
            return null;
        }

        try {
            return max(Arrays.asList(args));
        } catch (Throwable e) {
            String message = String.format("max(%s)", args);
            logError(message, e);
            return null;
        }
    }

    @Override
    public BigDecimal sum(Object... args) {
        if (args == null || args.length < 1) {
            return null;
        }

        try {
            return sum(Arrays.asList(args));
        } catch (Throwable e) {
            String message = String.format("sum(%s)", args);
            logError(message, e);
            return null;
        }
    }

    @Override
    public BigDecimal mean(List list) {
        if (list == null) {
            return null;
        }

        try {
            BigDecimal sum = sum(list);
            return numericDivide(sum, BigDecimal.valueOf(list.size())).stripTrailingZeros();
        } catch (Throwable e) {
            String message = String.format("mean(%s)", list);
            logError(message, e);
            return null;
        }
    }

    @Override
    public BigDecimal mean(Object... args) {
        if (args == null || args.length < 1) {
            return null;
        }

        try {
            return mean(Arrays.asList(args));
        } catch (Throwable e) {
            String message = String.format("mean(%s)", args);
            logError(message, e);
            return null;
        }
    }

    //
    // String functions
    //
    @Override
    public Boolean contains(String string, String match) {
        return (string == null || match == null) ? null : string.contains(match);
    }

    @Override
    public Boolean startsWith(String string, String match) {
        return (string == null || match == null) ? null : string.startsWith(match);
    }

    @Override
    public Boolean endsWith(String string, String match) {
        return (string == null || match == null) ? null : string.endsWith(match);
    }

    @Override
    public BigDecimal stringLength(String string) {
        return string == null ? null : BigDecimal.valueOf(string.length()).stripTrailingZeros();
    }

    @Override
    public String substring(String string, BigDecimal startPosition) {
        return substring(string, startPosition.intValue());
    }

    private String substring(String string, int startPosition) {
        if (startPosition < 0) {
            startPosition = string.length() + startPosition;
        } else {
            --startPosition;
        }
        return string.substring(startPosition);
    }

    @Override
    public String substring(String string, BigDecimal startPosition, BigDecimal length) {
        return substring(string, startPosition.intValue(), length.intValue());
    }

    private String substring(String string, int startPosition, int length) {
        if (startPosition < 0) {
            startPosition = string.length() + startPosition;
        } else {
            --startPosition;
        }
        return string.substring(startPosition, startPosition + length);
    }

    @Override
    public String upperCase(String string) {
        return string == null ? null : string.toUpperCase();
    }

    @Override
    public String lowerCase(String string) {
        return string == null ? null : string.toLowerCase();
    }

    @Override
    public String substringBefore(String string, String match) {
        if (string != null && match != null) {
            int i = string.indexOf(match);
            return i == -1 ? "" : string.substring(0, i);
        } else {
            return null;
        }
    }

    @Override
    public String substringAfter(String string, String match) {
        if (string != null && match != null) {
            int i = string.indexOf(match);
            return i == -1 ? "" : string.substring(i + match.length());
        } else {
            return null;
        }
    }

    @Override
    public String replace(String input, String pattern, String replacement) {
        return replace(input, pattern, replacement, "");
    }

    @Override
    public String replace(String input, String pattern, String replacement, String flags) {
        if (input == null || pattern == null || replacement == null) {
            return null;
        }
        if (flags == null) {
            flags = "";
        }

        try {
            String expression = String.format("replace(/root, '%s', '%s', '%s')", pattern, replacement, flags);
            return evaluateXPath(input, expression);
        } catch (Throwable e) {
            return null;
        }
    }

    @Override
    public Boolean matches(String input, String pattern) {
        return matches(input, pattern, "");
    }

    @Override
    public Boolean matches(String input, String pattern, String flags) {
        if (input == null || pattern == null) {
            return false;
        }
        if (flags == null) {
            flags = "";
        }

        try {
            String expression = String.format("/root[matches(., '%s', '%s')]", pattern, flags);
            String value = evaluateXPath(input, expression);
            return input.equals(value);
        } catch (Throwable e) {
            return null;
        }
    }

    private String evaluateXPath(String input, String expression) throws ParserConfigurationException, SAXException, IOException, XPathExpressionException {
        // Read document
        String xml = "<root>" + input + "</root>";
        DocumentBuilderFactory documentBuilderFactory = new DocumentBuilderFactoryImpl();
        DocumentBuilder docBuilder = documentBuilderFactory.newDocumentBuilder();
        InputStream inputStream = new ByteArrayInputStream(xml.getBytes());
        Document document = docBuilder.parse(inputStream);

        // Evaluate xpath
        XPathFactory xPathFactory = new XPathFactoryImpl();
        XPath xPath = xPathFactory.newXPath();
        return xPath.evaluate(expression, document.getDocumentElement());
    }

    //
    // Boolean functions
    //
    @Override
    public Boolean and(List list) {
        if (list == null) {
            return null;
        }

        try {
            if (list.stream().anyMatch(b -> b == Boolean.FALSE)) {
                return false;
            } else if (list.stream().allMatch(b -> b == Boolean.TRUE)) {
                return true;
            } else {
                return null;
            }
        } catch (Throwable e) {
            String message = String.format("and(%s)", list);
            logError(message, e);
            return null;
        }
    }

    @Override
    public Boolean or(List list) {
        if (list == null) {
            return null;
        }

        try {
            if (list.stream().anyMatch(b -> b == Boolean.TRUE)) {
                return true;
            } else if (list.stream().allMatch(b -> b == Boolean.FALSE)) {
                return false;
            } else {
                return null;
            }
        } catch (Throwable e) {
            String message = String.format("or(%s)", list);
            logError(message, e);
            return null;
        }
    }

    @Override
    public Boolean not(Boolean operand) {
        return booleanNot(operand);
    }

    @Override
    public Boolean and(Object... args) {
        if (args == null || args.length < 1) {
            return null;
        }

        try {
            return and(Arrays.asList(args));
        } catch (Throwable e) {
            String message = String.format("and(%s)", args);
            logError(message, e);
            return null;
        }
    }

    @Override
    public Boolean or(Object... args) {
        if (args == null || args.length < 1) {
            return null;
        }

        try {
            return or(Arrays.asList(args));
        } catch (Throwable e) {
            String message = String.format("or(%s)", args);
            logError(message, e);
            return null;
        }
    }

    //
    // Date functions
    //
    public BigDecimal year(LocalDate date) {
        try {
            return BigDecimal.valueOf(date.getYear());
        } catch (Exception e) {
            String message = String.format("year(%s)", date);
            logError(message, e);
            return null;
        }
    }
    public BigDecimal year(ZonedDateTime dateTime) {
        try {
            return BigDecimal.valueOf(dateTime.getYear());
        } catch (Exception e) {
            String message = String.format("year(%s)", dateTime);
            logError(message, e);
            return null;
        }
    }

    public BigDecimal month(LocalDate date) {
        try {
            return BigDecimal.valueOf(date.getMonth().getValue());
        } catch (Exception e) {
            String message = String.format("month(%s)", date);
            logError(message, e);
            return null;
        }
    }
    public BigDecimal month(ZonedDateTime dateTime) {
        try {
            return BigDecimal.valueOf(dateTime.getMonth().getValue());
        } catch (Exception e) {
            String message = String.format("month(%s)", dateTime);
            logError(message, e);
            return null;
        }
    }

    public BigDecimal day(LocalDate date) {
        try {
            return BigDecimal.valueOf(date.getDayOfMonth());
        } catch (Exception e) {
            String message = String.format("day(%s)", date);
            logError(message, e);
            return null;
        }
    }
    public BigDecimal day(ZonedDateTime dateTime) {
        try {
            return BigDecimal.valueOf(dateTime.getDayOfMonth());
        } catch (Exception e) {
            String message = String.format("day(%s)", dateTime);
            logError(message, e);
            return null;
        }
    }

    //
    // Time functions
    //
    public BigDecimal hour(OffsetTime time) {
        return BigDecimal.valueOf(time.getHour());
    }
    public BigDecimal hour(ZonedDateTime dateTime) {
        return BigDecimal.valueOf(dateTime.getHour());
    }

    public BigDecimal minute(OffsetTime time) {
        return BigDecimal.valueOf(time.getMinute());
    }
    public BigDecimal minute(ZonedDateTime dateTime) {
        return BigDecimal.valueOf(dateTime.getMinute());
    }

    public BigDecimal second(OffsetTime time) {
        return BigDecimal.valueOf(time.getSecond());
    }
    public BigDecimal second(ZonedDateTime dateTime) {
        return BigDecimal.valueOf(dateTime.getSecond());
    }

    public TemporalAmount timeOffset(OffsetTime time) {
        return timezone(time);
    }
    public TemporalAmount timeOffset(ZonedDateTime dateTime) {
        return timezone(dateTime);
    }

    public TemporalAmount timezone(OffsetTime time) {
        // timezone offset in minutes
        int minutesOffset = time.getOffset().getTotalSeconds() / 60;
        // Compute duration
        String sign = minutesOffset < 0 ? "-" : "";
        if (minutesOffset < 0) {
            minutesOffset = - minutesOffset;
        }
        int days = minutesOffset / (24 * 60);
        int hours = minutesOffset % (24 * 60) / 60;
        int minutes = minutesOffset % 60;
        String dayTimeDuration = String.format("%sP%dDT%dH%dM", sign, days, hours, minutes);
        return duration(dayTimeDuration);
    }
    public TemporalAmount timezone(ZonedDateTime dateTime) {
        // timezone offset in minutes
        int minutesOffset = dateTime.getOffset().getTotalSeconds() / 60;
        // Compute duration
        String sign = minutesOffset < 0 ? "-" : "";
        if (minutesOffset < 0) {
            minutesOffset = - minutesOffset;
        }
        int days = minutesOffset / (24 * 60);
        int hours = minutesOffset % (24 * 60) / 60;
        int minutes = minutesOffset % 60;
        String dayTimeDuration = String.format("%sP%dDT%dH%dM", sign, days, hours, minutes);
        return duration(dayTimeDuration);
    }

    //
    // Duration functions
    //
    public BigDecimal years(TemporalAmount duration) {
        return BigDecimal.valueOf(duration.get(ChronoUnit.YEARS));
    }

    public BigDecimal months(TemporalAmount duration) {
        return BigDecimal.valueOf(duration.get(ChronoUnit.MONTHS));
    }

    public BigDecimal days(TemporalAmount duration) {
        return BigDecimal.valueOf(duration.get(ChronoUnit.DAYS));
    }

    public BigDecimal hours(TemporalAmount duration) {
        return BigDecimal.valueOf(duration.get(ChronoUnit.HOURS));
    }

    public BigDecimal minutes(TemporalAmount duration) {
        return BigDecimal.valueOf(duration.get(ChronoUnit.MINUTES));
    }

    public BigDecimal seconds(TemporalAmount duration) {
        return BigDecimal.valueOf(duration.get(ChronoUnit.SECONDS));
    }

    private int months(ZonedDateTime calendar) {
        return calendar.getYear() * 12 + calendar.getMonth().getValue();
    }

    //
    // List functions
    //
    @Override
    public Boolean listContains(List list, Object element) {
        return list == null ? null : list.contains(element);
    }

    @Override
    public List append(List list, Object... items) {
        List result = new ArrayList<>();
        if (list != null) {
            result.addAll(list);
        }
        if (items != null) {
            for (Object item : items) {
                result.add(item);
            }
        } else {
            result.add(null);
        }
        return result;
    }

    @Override
    public BigDecimal count(List list) {
        return list == null ? BigDecimal.valueOf(0) : BigDecimal.valueOf(list.size());
    }

    @Override
    public BigDecimal min(List list) {
        if (list == null || list.isEmpty()) {
            return null;
        }

        try {
            BigDecimal result = (BigDecimal) list.get(0);
            for (int i = 1; i < list.size(); i++) {
                BigDecimal x = (BigDecimal) list.get(i);
                if (result.compareTo(x) > 0) {
                    result = x;
                }
            }
            return result;
        } catch (Throwable e) {
            String message = String.format("min(%s)", list);
            logError(message, e);
            return null;
        }
    }

    @Override
    public BigDecimal max(List list) {
        if (list == null || list.isEmpty()) {
            return null;
        }

        try {
            BigDecimal result = (BigDecimal) list.get(0);
            for (int i = 1; i < list.size(); i++) {
                BigDecimal x = (BigDecimal) list.get(i);
                if (result.compareTo(x) < 0) {
                    result = x;
                }
            }
            return result;
        } catch (Throwable e) {
            String message = String.format("max(%s)", list);
            logError(message, e);
            return null;
        }
    }

    @Override
    public BigDecimal sum(List list) {
        if (list == null || list.isEmpty()) {
            return null;
        }

        try {
            BigDecimal result = BigDecimal.valueOf(0);
            for (int i = 0; i < list.size(); i++) {
                BigDecimal x = (BigDecimal)list.get(i);
                result = result.add(x);
            }
            return result.stripTrailingZeros();
        } catch (Throwable e) {
            String message = String.format("sum(%s)", list);
            logError(message, e);
            return null;
        }
    }

    @Override
    public List sublist(List list, BigDecimal startPosition) {
        return sublist(list, startPosition.intValue());
    }

    @Override
    public List sublist(List list, BigDecimal startPosition, BigDecimal length) {
        return sublist(list, startPosition.intValue(), length.intValue());
    }

    private List sublist(List list, int position) {
        List result = new ArrayList<>();
        if (list == null || isOutOfBounds(list, position)) {
            return result;
        }
        int javaStartPosition;
        // up to, not included
        int javaEndPosition = list.size();
        if (position < 0) {
            javaStartPosition = list.size() + position;
        } else {
            javaStartPosition = position - 1;
        }
        for (int i = javaStartPosition; i < javaEndPosition; i++) {
            result.add(list.get(i));
        }
        return result;
    }

    private List sublist(List list, int position, int length) {
        List result = new ArrayList<>();
        if (list == null || isOutOfBounds(list, position)) {
            return result;
        }
        int javaStartPosition;
        int javaEndPosition;
        if (position < 0) {
            javaStartPosition = list.size() + position;
            javaEndPosition = javaStartPosition + length;
        } else {
            javaStartPosition = position - 1;
            javaEndPosition = javaStartPosition + length;
        }
        for (int i = javaStartPosition; i < javaEndPosition; i++) {
            result.add(list.get(i));
        }
        return result;
    }

    private boolean isOutOfBounds(List list, int position) {
        int length = list.size();
        if (position < 0) {
            return !(-length <= position && position <= -1);
        } else {
            return !(1 <= position && position <= length);
        }
    }

    @Override
    public List concatenate(Object... lists) {
        List result = new ArrayList<>();
        if (lists != null) {
            for (Object list : lists) {
                result.addAll((List) list);
            }
        }
        return result;
    }

    @Override
    public List insertBefore(List list, BigDecimal position, Object newItem) {
        return insertBefore(list, position.intValue(), newItem);
    }

    private List insertBefore(List list, int position, Object newItem) {
        List result = new ArrayList<>();
        if (list != null) {
            result.addAll(list);
        }
        if (isOutOfBounds(result, position)) {
            return result;
        }
        if (position < 0) {
            position = result.size() + position;
        } else {
            position = position - 1;
        }
        result.add(position, newItem);
        return result;
    }

    @Override
    public List remove(List list, Object position) {
        return remove(list, ((BigDecimal) position).intValue());
    }

    private List remove(List list, int position) {
        List result = new ArrayList<>();
        if (list != null) {
            result.addAll(list);
        }
        result.remove(position - 1);
        return result;
    }

    @Override
    public List reverse(List list) {
        List result = new ArrayList<>();
        if (list != null) {
            for (int i = list.size() - 1; i >= 0; i--) {
                result.add(list.get(i));
            }
        }
        return result;
    }

    @Override
    public List indexOf(List list, Object match) {
        List result = new ArrayList<>();
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                Object o = list.get(i);
                if (o == null && match == null || o!= null && o.equals(match)) {
                    result.add(BigDecimal.valueOf(i + 1));
                }
            }
        }
        return result;
    }

    @Override
    public List union(Object... lists) {
        List result = new ArrayList<>();
        if (lists != null) {
            for (Object list : lists) {
                result.addAll((List) list);
            }
        }
        return distinctValues(result);
    }

    @Override
    public List distinctValues(List list1) {
        List result = new ArrayList<>();
        if (list1 != null) {
            for (Object element : list1) {
                if (!result.contains(element)) {
                    result.add(element);
                }
            }
        }
        return result;
    }

    @Override
    public List flatten(List list1) {
        if (list1 == null) {
            return null;
        }
        List result = new ArrayList<>();
        collect(result, list1);
        return result;
    }

    @Override
    public void collect(List result, List list) {
        if (list != null) {
            for (Object object : list) {
                if (object instanceof List) {
                    collect(result, (List) object);
                } else {
                    result.add(object);
                }
            }
        }
    }

    @Override
    public <T extends Object> List<T> sort(List<T> list, LambdaExpression<Boolean> comparator) {
        List<T> clone = new ArrayList<>(list);
        Comparator<? super T> comp = new Comparator<T>() {
            @Override
            public int compare(T o1, T o2) {
                if (comparator.apply(o1, o2)) {
                    return -1;
                } else if (o1 != null && o1.equals(o2)) {
                    return 0;
                } else {
                    return 1;
                }
            }
        };
        Collections.sort(clone, comp);
        return clone;
    }

    //
    // Extra functions
    //
    @Override
    public<T> List<T> asList(T ...objects) {
        if (objects == null) {
            List<T> result = new ArrayList<>();
            result.add(null);
            return result;
        } else {
            return Arrays.asList(objects);
        }
    }

    @Override
    public<T> T asElement(List<T> list) {
        if (list == null) {
            return null;
        } else if (list.size() == 1) {
            return list.get(0);
        } else {
            return null;
        }
    }

    @Override
    public List<BigDecimal> rangeToList(boolean isOpenStart, BigDecimal start, boolean isOpenEnd, BigDecimal end) {
        List<BigDecimal> result = new ArrayList<>();
        if (start == null || end == null) {
            return result;
        }
        int startValue = isOpenStart ? start.intValue() + 1 : start.intValue();
        int endValue = isOpenEnd ? end.intValue() - 1 : end.intValue();
        for (int i = startValue; i <= endValue; i++) {
            result.add(BigDecimal.valueOf(i));
        }
        return result;
    }

    @Override
    public List flattenFirstLevel(List list) {
        if (list == null) {
            return null;
        }
        List result = new ArrayList<>();
        for (Object object : list) {
            if (object instanceof List) {
                result.addAll((List) object);
            } else {
                result.add(object);
            }
        }
        return result;
    }

    @Override
    public Object elementAt(List list, BigDecimal index) {
        return elementAt(list, index.intValue());
    }

    private Object elementAt(List list, int index) {
        if (list == null) {
            return null;
        }
        int listSize = list.size();
        if (1 <= index && index <= listSize) {
            return list.get(index - 1);
        } else if (-listSize <= index && index <= -1) {
            return list.get(listSize + index);
        } else {
            logError(String.format("Index '%s' out of bounds [1, %s]", index, list.size()));
            return null;
        }
    }

    private String dateTimeISOFormat(BigDecimal year, BigDecimal month, BigDecimal day, int hours, int minutes, int seconds) {
        return String.format("%04d-%02d-%02dT%02d:%02d:%02dZ", year.intValue(), month.intValue(), day.intValue(), hours, minutes, seconds);
    }

    private String dateTimeISOFormat(int year, int month, int day, int hours, int minutes, int seconds, String offset) {
        return String.format("%04d-%02d-%02dT%02d:%02d:%02d%s", year, month, day, hours, minutes, seconds, offset);
    }

    protected void logError(String message) {
        LOGGER.error(message);
    }

    protected void logError(String message, Throwable e) {
        LOGGER.error(message, e);
    }
}