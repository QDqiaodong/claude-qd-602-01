package com.music.instrument.dto.view;

import java.util.List;

import com.music.instrument.domain.Instrument;

/**
 * 乐器行内展开详情：该乐器的租赁历史 + 维修历史。
 */
public class InstrumentHistoryView {

    private Instrument instrument;
    private List<RentalView> rentals;
    private List<ServiceView> services;

    public Instrument getInstrument() {
        return instrument;
    }

    public void setInstrument(Instrument instrument) {
        this.instrument = instrument;
    }

    public List<RentalView> getRentals() {
        return rentals;
    }

    public void setRentals(List<RentalView> rentals) {
        this.rentals = rentals;
    }

    public List<ServiceView> getServices() {
        return services;
    }

    public void setServices(List<ServiceView> services) {
        this.services = services;
    }
}
