package qa.cinepolis.backend.model;

import java.util.List;

public class VideoQueryResult {

    private List<VideoRecord> items;
    private int total;
    private int page;
    private int pageSize;

    public VideoQueryResult() {}

    public VideoQueryResult(List<VideoRecord> items, int total, int page, int pageSize) {
        this.items    = items;
        this.total    = total;
        this.page     = page;
        this.pageSize = pageSize;
    }

    public List<VideoRecord> getItems()    { return items; }
    public int               getTotal()    { return total; }
    public int               getPage()     { return page; }
    public int               getPageSize() { return pageSize; }
}
