package home.quiet.tv;

import java.util.Calendar;

/** Local foreground-use budgets. Locking never changes the saved time rule. */
final class ChildModePolicy {
    static final int NONE=0,SINGLE=1,DAILY=2;
    static final long MINUTE=60_000L,DAY=24*60*MINUTE;
    // Per-day limits ordered Monday..Sunday; 0 means "follow the shared limit".
    static final int WEEK_DAYS=7;
    static final class Rule {
        boolean hidden,locked;
        int mode;
        long limit=60*MINUTE,singleUsed,dailyUsed,singleExtra,dailyExtra;
        long[] weekLimits;
        String day="";
        Rule copy(){Rule r=new Rule();r.hidden=hidden;r.locked=locked;r.mode=mode;r.limit=limit;r.singleUsed=singleUsed;r.dailyUsed=dailyUsed;r.singleExtra=singleExtra;r.dailyExtra=dailyExtra;r.day=day;if(weekLimits!=null)r.weekLimits=weekLimits.clone();return r;}
        boolean limited(){return mode==SINGLE || mode==DAILY;}
        boolean weekly(){return weekLimits!=null && weekLimits.length==WEEK_DAYS;}
        long dayLimit(int dayOfWeek){
            // Calendar SUNDAY=1..SATURDAY=7; weekLimits is Monday-first.
            if(!weekly())return limit;
            int index=(dayOfWeek+Calendar.SATURDAY-1)%WEEK_DAYS;
            long perDay=weekLimits[index];
            return perDay>0?perDay:limit;
        }
        void validate(){
            if(mode<NONE || mode>DAILY || limit<MINUTE || limit>DAY || singleUsed<0 || dailyUsed<0 || singleExtra<0 || singleExtra>DAY || dailyExtra<0 || dailyExtra>DAY)throw new IllegalArgumentException("儿童模式规则无效");
            if(weekLimits!=null){
                if(weekLimits.length!=WEEK_DAYS)throw new IllegalArgumentException("儿童模式规则无效");
                for(long value:weekLimits)if(value<0 || value>DAY)throw new IllegalArgumentException("儿童模式规则无效");
            }
        }
        void newDay(String today){
            // Clock rollback must not grant a fresh budget for an already visited date.
            if(day.isEmpty() || today.compareTo(day)>0){day=today;dailyUsed=0;dailyExtra=0;}
        }
        long remaining(){return remaining(Calendar.getInstance().get(Calendar.DAY_OF_WEEK));}
        long remaining(int dayOfWeek){
            if(!limited())return Long.MAX_VALUE;
            long budget=mode==SINGLE?limit:dayLimit(dayOfWeek);
            return Math.max(0,budget+(mode==SINGLE?singleExtra:dailyExtra)-(mode==SINGLE?singleUsed:dailyUsed));
        }
        void consume(long elapsed,String today,long sinceMidnight){
            if(elapsed<0)throw new IllegalArgumentException("计时间隔无效");
            boolean changed=!day.isEmpty() && today.compareTo(day)>0;newDay(today);
            if(!limited())return;
            long amount=Math.min(elapsed,remaining());
            if(mode==DAILY && changed)amount=Math.min(amount,Math.max(0,sinceMidnight));
            if(mode==SINGLE)singleUsed+=amount;else dailyUsed+=amount;
        }
        void grant(long amount){
            if(!limited() || amount<MINUTE || amount>DAY)throw new IllegalArgumentException("追加时间需为 1 分钟至 24 小时");
            if(mode==SINGLE){if(singleExtra+amount>DAY)throw new IllegalArgumentException("本次追加时间已达上限");singleExtra+=amount;}
            else{if(dailyExtra+amount>DAY)throw new IllegalArgumentException("今日追加时间已达上限");dailyExtra+=amount;}
        }
    }
    static final class TriplePress {
        private int count;
        private long last;
        boolean press(long now){if(count==0 || now<last || now-last>2000)count=0;last=now;return ++count>=3;}
        void clear(){count=0;last=0;}
    }
}
