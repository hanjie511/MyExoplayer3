package com.example.exoplayer;

import android.media.MediaMetadata;
import android.support.v4.media.MediaMetadataCompat;

import java.util.List;

public class QueueManager {
    private int index;
    private List<MediaMetadataCompat> list;
    public QueueManager(){
        index=0;
        list=Samples.getPlayList();
    }
    public int getIndex() {
        return index;
    }
    public MediaMetadataCompat getNextMediaMetadata(){
        if(index<list.size()-1){
            index++;
        }else{
            index=0;
        }
        return list.get(index);
    }
    public MediaMetadataCompat getPreviousMediaMetadata(){
        if(index>0){
            index--;
        }else{
            index=list.size()-1;
        }
        return list.get(index);
    }
    public MediaMetadataCompat getCurrentMediaMetadata(){
        return list.get(index);
    }
}
