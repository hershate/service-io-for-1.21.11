package net.thenextlvl.service.plugin.wrapper.service.model;

import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;
import net.thenextlvl.service.economy.currency.Currency;
import net.thenextlvl.service.economy.currency.CurrencyData;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public record WrappedCurrency(Economy economy) implements Currency {
    @Override
    public String getName() {
        return economy.getName();
    }

    @Override
    public Optional<Component> getDisplayNameSingular(final Locale locale) {
        return Optional.ofNullable(economy.currencyNameSingular()).map(Component::text);
    }

    @Override
    public Optional<Component> getDisplayNamePlural(final Locale locale) {
        return Optional.ofNullable(economy.currencyNamePlural()).map(Component::text);
    }

    @Override
    public CurrencyData toData() {
        final var singular = new HashMap<Locale, Component>();
        final var plural = new HashMap<Locale, Component>();
        final var nameSingular = economy.currencyNameSingular();
        final var namePlural = economy.currencyNamePlural();
        if (nameSingular != null) singular.put(Locale.US, Component.text(nameSingular));
        if (namePlural != null) plural.put(Locale.US, Component.text(namePlural));
        return CurrencyData.of(getName(), getSymbol(), getFractionalDigits(), singular, plural);
    }

    @Override
    public Component getSymbol() {
        return Component.empty();
    }

    @Override
    public Component format(final Number amount, final Locale locale) {
        return Component.text(economy.format(amount.doubleValue()));
    }

    @Override
    public int getFractionalDigits() {
        return economy.fractionalDigits();
    }
}