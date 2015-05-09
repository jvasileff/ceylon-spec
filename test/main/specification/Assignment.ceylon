void testAssignmentInExpression() {
    variable Boolean b;
    value x = true -> (b=true);
    print(b);
}
void testOkAssignmentInExpression() {
    variable Boolean b;
    value x = (b=true) || false;
    value y = true && (b=true);
    print(b);
}
void testBrokenAssignmentInExpression() {
    variable Boolean b;
    value x = true || (b=true);
    value y = true && (b=true);
    @error print(b);
}
void testAnotherBrokenAssignmentInExpression() {
    variable Boolean b;
    value x = (String s) => (b=true);
    @error print(b);
}

void testAssignmentInDec() {
    variable Boolean b;
    Boolean c = (b=true);
    print(b);
}
void testAssignmentInArg() {
    variable Boolean b;
    print(b=true);
    print(b);
}
void testAssignmentInNamedArg() {
    variable Boolean b;
    print { val=(b=true); };
    print(b);
}

void testAssignmentInIf() {
    variable Boolean b;
    if (b=true) {}
    print(b);
}
void testAssignmentInIf1() {
    variable Boolean b;
    if (true||(b=true)) {}
    @error print(b);
}
void testAssignmentInIf2() {
	interface I {}
    variable Boolean b;
    @error if (is I i = (b=true)) {}
    print(b);
}
void testAssignmentInIf3() {
	interface I {}
	Object hello="hello";
    variable Object o="hello";
    if (is String s = (o=hello)) {}
    print(o);
}

void testAssignmentInWhile() {
    variable Boolean b;
    while (b=true) {}
    print(b);
}
void testAssignmentInWhile1() {
    variable Boolean b;
    while (true||(b=true)) {}
    @error print(b);
}
void testAssignmentInWhile2() {
	interface I {}
    variable Boolean b;
    @error while (is I i = (b=true)) {}
    print(b);
}
void testAssignmentInWhile3() {
	interface I {}
	Object hello="hello";
    variable Object o="hello";
    if (is String s = (o=hello)) {}
    print(o);
}

void testForwardLazySpecifier() {
    String name1;
    String name2;
    variable String name3;
    variable String name4;
    @error //TODO: should not display error here!
    name1 = "";
    name2 => "";
    name3 = "";
    @error name4 => "";
    @error name1 => "";
    @error name2 => "";
    @error name3 => "";
    @error name4 => "";
}

void withLocalCircular() {
    @error Integer circular = circular;
    @error Integer recursize => recursize;
    Integer split;
    @error split = split;
}
@error Integer circular = circular;
@error Integer recursize => recursize;

