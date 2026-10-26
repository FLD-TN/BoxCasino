package com.dex.boxcasino.music;

import com.github.Shimado.boomboxapi.instances.BoomboxSong;
import com.github.Shimado.boomboxapi.instances.NbsSong;
import java.util.List;

/** One .nbs song of plugins/BoxCasino/music, in the form the Boombox player wants. */
final class CasinoSong implements BoomboxSong {

    private final String id;
    private NbsSong nbs;

    CasinoSong(String id, NbsSong nbs) {
        this.id = id;
        this.nbs = nbs;
    }

    @Override
    public String getID() {
        return id;
    }

    @Override
    public String getFilename() {
        return id;
    }

    @Override
    public String getMaterialUnPurchased() {
        return null;
    }

    @Override
    public Object getCustomModelDataUnPurchased() {
        return null;
    }

    @Override
    public String getMaterialPurchased() {
        return null;
    }

    @Override
    public Object getCustomModelDataPurchased() {
        return null;
    }

    @Override
    public String getMaterialPlaylist() {
        return null;
    }

    @Override
    public Object getCustomModelDataPlaylist() {
        return null;
    }

    @Override
    public String getName() {
        return id;
    }

    @Override
    public List<String> getDescription() {
        return List.of();
    }

    @Override
    public double getPrice() {
        return 0;
    }

    @Override
    public List<Integer> getSlots() {
        return List.of();
    }

    @Override
    public int getPage() {
        return -1;
    }

    @Override
    public String getPermission() {
        return "";
    }

    @Override
    public int getOwn() {
        return 0;
    }

    @Override
    public void addOwn() {
    }

    @Override
    public void setOwn(int own) {
    }

    @Override
    public int getAuditions() {
        return 0;
    }

    @Override
    public void addAuditions() {
    }

    @Override
    public void setAuditions(int auditions) {
    }

    @Override
    public NbsSong getNbsSong() {
        return nbs;
    }

    @Override
    public void setNbsSong(NbsSong nbs) {
        this.nbs = nbs;
    }

    @Override
    public String getCustomOggSoundName() {
        return null;
    }

    @Override
    public int getCustomOggSoundLength() {
        return 0;
    }
}
