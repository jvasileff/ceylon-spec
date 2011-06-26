interface ClassBodies {
    
    class GoodWithCircular() {
        String name = "gavin";
        void x() { y(); }
        void y() { x(); }
    }
    
    object goodWithCircular {
        String name = "gavin";
        void x() { y(); }
        void y() { x(); }
    }
    
    class Good2WithCircular() {
        void x() { y(); }
        void y() { x(); }
    }
    
    object good2WithCircular {
        void x() { y(); }
        void y() { x(); }
    }
    
    class Good3WithCircular() {
        String name = "gavin";
        void x() { this.y(); }
        void y() { this.x(); }
    }
    
    object good3WithCircular {
        String name = "gavin";
        void x() { this.y(); }
        void y() { this.x(); }
    }
    
    class Good4WithCircular() {
        void x() { this.y(); }
        void y() { this.x(); }
    }
    
    object good4WithCircular {
        void x() { this.y(); }
        void y() { this.x(); }
    }
    
    class BadWithCircular() {
        void x() { @error y(); }
        void y() { x(); }
        String name = "gavin";
    }
    
    object badWithCircular {
        void x() { @error y(); }
        void y() { x(); }
        String name = "gavin";
    }
    
    class Bad2WithCircular() {
        void x() { @error this.y(); }
        void y() { this.x(); }
        String name = "gavin";
    }
    
    object bad2WithCircular {
        void x() { @error this.y(); }
        void y() { this.x(); }
        String name = "gavin";
    }
    
    class GoodWithInner() {
        String name = "gavin";
        class Inner() {
            x();
        }
        String x() { return "Hell"; }
    }
    
    class BadWithInner() {
        class Inner() {
            @error x();
        }
        String x() { return "Hell"; }
        String name = "gavin";
    }
    
    class Good2WithInner() {
        String name = "gavin";
        class Inner() {
            void y() {
                x();
            }
        }
        String x() { return "Hell"; }
    }
    
    class Bad2WithInner() {
        class Inner() {
            void y() {
                @error x();
            }
        }
        String x() { return "Hell"; }
        String name = "gavin";
    }
    
    object goodWithInner {
        String name = "gavin";
        class Inner() {
            x();
        }
        String x() { return "Hell"; }
    }
    
    object badWithInner {
        class Inner() {
            @error x();
        }
        String x() { return "Hell"; }
        String name = "gavin";
    }
    
    object good2WithInner {
        String name = "gavin";
        class Inner() {
            void y() {
                x();
            }
        }
        String x() { return "Hell"; }
    }
    
    object bad2WithInner {
        class Inner() {
            void y() {
                @error x();
            }
        }
        String x() { return "Hell"; }
        String name = "gavin";
    }
    
    class Good3WithInner() {
        String name = "gavin";
        class Inner() {
            @type["String"] outer.x();
            void x() {}
        }
        String x() { return "Hell"; }
    }
    
    class Bad3WithInner() {
        class Inner() {
            void x() {}
            @error outer.x();
        }
        String x() { return "Hell"; }
        String name = "gavin";
    }
    
    class Good4WithInner() {
        String name = "gavin";
        class Inner() {
            void x() {}
            void y() {
                @type["String"] outer.x();
            }
        }
        String x() { return "Hell"; }
    }
    
    class Bad4WithInner() {
        class Inner() {
            void y() {
                void x() {}
                @error outer.x();
            }
        }
        String x() { return "Hell"; }
        String name = "gavin";
    }
    
    object good3WithInner {
        String name = "gavin";
        object inner {
            @type["String"] outer.x();
            void x() {}
        }
        String x() { return "Hell"; }
    }
    
    object bad3WithInner {
        object inner {
            void x() {}
            @error outer.x();
        }
        String x() { return "Hell"; }
        String name = "gavin";
    }
    
    object good4WithInner {
        String name = "gavin";
        object inner {
            void y() {
                @type["String"] outer.x();
            }
            void x() {}
        }
        String x() { return "Hell"; }
    }
    
    object bad4WithInner {
        object inner {
            void x() {}
            void y() {
                @error outer.x();
            }
        }
        String x() { return "Hell"; }
        String name = "gavin";
    }
    
    class GoodWithThis() {
        String name = "gavin";
        local get { return this; }
    }
    
    object goodWithThis {
        String name = "gavin";
        local get { return this; }
    }
    
    class BadWithThis() {
        local get { @error return this; }
        String name = "gavin";
    }
    
    object badWithThis {
        local get { @error return this; }
        String name = "gavin";
    }
    
    void print(Object o) {}
    
    class Good2WithThis() {
        String name = "gavin";
        void print() { outer.print(this); }
    }
    
    object good2WithThis {
        String name = "gavin";
        void print() { outer.print(this); }
    }
    
    class Bad2WithThis() {
        void print() { @error outer.print(this); }
        String name = "gavin";
    }
    
    object bad2WithThis {
        void print() { @error outer.print(this); }
        String name = "gavin";
    }
    
    class Good3WithThis() {
        String name = "gavin";
        void member() {
            local t = this;
        }
    }
    
    object good3WithThis {
        String name = "gavin";
        void member() {
            local t = this;
        }
    }
    
    class Bad3WithThis() {
        String name = "gavin";
        @error local t = this;
    }
    
    object bad3WithThis {
        String name = "gavin";
        @error local t = this;
    }
    
    class Good4WithThis() {
        String name = "gavin";
        void member() {
            variable local t := this;
        }
    }
    
    object good4WithThis {
        String name = "gavin";
        void member() {
            variable local t := this;
        }
    }
    
    class Bad4WithThis() {
        String name = "gavin";
        @error variable local t := this;
    }
    
    object bad4WithThis {
        String name = "gavin";
        @error variable local t := this;
    }
    
    class Good5WithThis() {
        String name = "gavin";
        variable Good5WithThis? t := null;
        void member() {
            t := this;
        }
    }
    
    object good5WithThis {
        String name = "gavin";
        variable Object? t := null;
        void member() {
            t := this;
        }
    }
    
    class Bad5WithThis() {
        String name = "gavin";
        variable Bad5WithThis? t := null;
        @error t := this;
    }
    
    object bad5WithThis {
        String name = "gavin";
        variable Object? t := null;
        @error t := this;
    }
    
    class GoodWithOuter() {
        String name = "gavin";
        class Inner() {
            GoodWithOuter o { 
                return outer;
            }
        }
    }
    
    class BadWithOuter() {
        class Inner() {
            BadWithOuter o { 
                @error return outer;
            }
        }
        String name = "gavin";
    }
    
    object goodWithOuter {
        String name = "gavin";
        object inner {
            local o { 
                return outer;
            }
        }
    }
    
    object badWithOuter {
        object inner {
            local o { 
                @error return outer;
            }
        }
        String name = "gavin";
    }
    
    class Good2WithOuter() {
        String name = "gavin";
        class Inner() {
            print(outer);
        }
    }
    
    class Bad2WithOuter() {
        class Inner() {
            @error print(outer);
        }
        String name = "gavin";
    }
    
    object good2WithOuter {
        String name = "gavin";
        object inner {
            print(outer);
        }
    }
    
    object bad2WithOuter {
        object inner {
            @error print(outer);
        }
        String name = "gavin";
    }
    
    class Good3WithOuter() {
        String name = "gavin";
        class Inner() {
            local o = outer;
        }
    }
    
    class Bad3WithOuter() {
        class Inner() {
            @error local o = outer;
        }
        String name = "gavin";
    }
    
    object good3WithOuter {
        String name = "gavin";
        object inner {
            local o = outer;
        }
    }
    
    object bad3WithOuter {
        object inner {
            @error local o = outer;
        }
        String name = "gavin";
    }
    
    class Good4WithOuter() {
        String name = "gavin";
        variable Object? o := null;
        class Inner() {
            o := outer;
        }
    }
    
    class Bad4WithOuter() {
        variable Object? o := null;
        class Inner() {
            @error o := outer;
        }
        String name = "gavin";
    }
    
    object good4WithOuter {
        String name = "gavin";
        variable Object? o := null;
        object inner {
            o := outer;
        }
    }
    
    object bad4WithOuter {
        variable Object? o := null;
        object inner {
            @error o := outer;
        }
        String name = "gavin";
    }
    
    class Super() {
        shared String name="gavin";
    }
    
    class BadWithSuper() extends Super() {
        void inner() {
            String n = super.name;
            @error Object o = super;
            @error print(super);
            @error return super;
        }
    }
    
}