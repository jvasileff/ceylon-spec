package com.redhat.ceylon.compiler.typechecker.analyzer;

import java.util.List;

import com.redhat.ceylon.compiler.typechecker.model.Class;
import com.redhat.ceylon.compiler.typechecker.model.IntersectionType;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.TypeAlias;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.TypeParameter;
import com.redhat.ceylon.compiler.typechecker.model.UnionType;
import com.redhat.ceylon.compiler.typechecker.model.UnknownType;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;

/**
 * Detects and eliminates potentially undecidable 
 * supertypes, including: 
 * - supertypes containing intersections in type 
 *   arguments, and
 * - supertypes with incorrect variance.
 * 
 * @author Gavin King
 *
 */
public class SupertypeVisitor extends Visitor {

    private static boolean isUndecidableSupertype(ProducedType st,
            Node node) {
        if (st==null) return false;
        TypeDeclaration std = st.getDeclaration();
        if (std instanceof TypeAlias) {
            ProducedType et = std.getExtendedType();
            if (et.isRecursiveTypeAliasDefinition(std)) { 
                //TODO: we could get rid of this check if
                //      we introduced and additional
                //      compilation phase to separate
                //      SupertypeVisitor/AliasVisitor
                node.addError("supertype contains reference to circularly defined type alias");
                return true;
            }
            else if (isUndecidableSupertype(et, node)) {
                return true;
            }
        }
        List<ProducedType> tal = st.getTypeArgumentList();
        List<TypeParameter> tpl = std.getTypeParameters();
        for (int i=0; i<tal.size() && i<tpl.size(); i++) {
            TypeParameter tp = tpl.get(i);
            ProducedType at = tal.get(i);
            if (!tp.isCovariant() && !tp.isContravariant()) {
                if (containsIntersection(at, node)) {
                    return true;
                }
            }
            if (isUndecidableSupertype(at, node)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsIntersection(ProducedType at,
            Node node) {
        if (at==null) return false;
        TypeDeclaration atd = at.getDeclaration();
        if (atd instanceof TypeAlias) {
            ProducedType et = atd.getExtendedType();
            if (et.isRecursiveTypeAliasDefinition(atd)) {
                //TODO: we could get rid of this check if
                //      we introduced and additional
                //      compilation phase to separate
                //      SupertypeVisitor/AliasVisitor
                node.addError("supertype contains reference to circularly defined type alias");
                return true;
            }
            else if (containsIntersection(et, node)) {
                return true;
            }
        }
        if (atd instanceof IntersectionType) {
            node.addError("supertype contains intersection as argument to invariant type parameter: " +
                    at.getProducedTypeName(node.getUnit()));
            return true;
        }
        if (atd instanceof UnionType) {
            for (ProducedType ct: atd.getCaseTypes()) {
                if (containsIntersection(ct, node)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void checkSupertypeVariance(Tree.SatisfiedTypes that, TypeDeclaration d) {
        if (that!=null) {
            int i=0;
            for (Tree.StaticType st: that.getTypes()) {
                ProducedType type = st.getTypeModel();
                if (type.isRecursiveTypeAliasDefinition(d)) {
                    //TODO: we could get rid of this check if
                    //      we introduced and additional
                    //      compilation phase to separate
                    //      SupertypeVisitor/AliasVisitor
                    st.addError("supertype contains reference to circularly defined type alias");
                }
                else {
                    List<TypeDeclaration> errors = type.resolveAliases().checkDecidability();
                    for (TypeDeclaration td: errors) {
                        that.addError("type with contravariant type parameter " + td.getName() + 
                                " appears in contravariant location in satisfied type: " + 
                                type.getProducedTypeName(that.getUnit()));
                    }
                    if (!errors.isEmpty()) {
                        d.getSatisfiedTypes().set(i, new UnknownType(that.getUnit()).getType());
                    }
                    i++;
                }
            }
        }
    }

    private void checkSupertypeVariance(Tree.ExtendedType that, Class d) {
        if (that!=null) {
            Tree.StaticType et = that.getType();
            ProducedType type = et.getTypeModel();
            if (type.isRecursiveTypeAliasDefinition(d)) {
                //TODO: we could get rid of this check if
                //      we introduced and additional
                //      compilation phase to separate
                //      SupertypeVisitor/AliasVisitor
                et.addError("supertype contains reference to circularly defined type alias");
            }
            else {
                List<TypeDeclaration> errors = type.resolveAliases().checkDecidability();
                for (TypeDeclaration td: errors) {
                    that.addError("type with contravariant type parameter " + td.getName() + 
                            " appears in contravariant location in extended type: " + 
                            type.getProducedTypeName(that.getUnit()));
                }
                if (!errors.isEmpty()) {
                    d.setExtendedType(new UnknownType(that.getUnit()).getType());
                }
            }
        }
    }

    private void checkForUndecidability(Tree.SatisfiedTypes that, TypeDeclaration d) {
        if (that!=null) {
            int i=0;
            for (Tree.StaticType st: that.getTypes()) {
                if (isUndecidableSupertype(st.getTypeModel(), st)) {
                    d.getSatisfiedTypes().set(i, new UnknownType(that.getUnit()).getType());
                }
                i++;
            }
        }
    }

    private void checkForUndecidability(Tree.ExtendedType that, Class d) {
        if (that!=null) {
            Tree.StaticType et = that.getType();
            if (isUndecidableSupertype(et.getTypeModel(), et)) {
                d.setExtendedType(new UnknownType(that.getUnit()).getType());
            }
        }
    }

    @Override 
    public void visit(Tree.ClassDefinition that) {
        super.visit(that);
        checkForUndecidability(that.getSatisfiedTypes(), that.getDeclarationModel());
        checkForUndecidability(that.getExtendedType(), that.getDeclarationModel());
        checkSupertypeVariance(that.getExtendedType(), that.getDeclarationModel());
        checkSupertypeVariance(that.getSatisfiedTypes(), that.getDeclarationModel());
    }

    @Override 
    public void visit(Tree.InterfaceDefinition that) {
        super.visit(that);
        checkForUndecidability(that.getSatisfiedTypes(), that.getDeclarationModel());
        checkSupertypeVariance(that.getSatisfiedTypes(), that.getDeclarationModel());
    }

    @Override 
    public void visit(Tree.TypeConstraint that) {
        super.visit(that);
        checkForUndecidability(that.getSatisfiedTypes(), that.getDeclarationModel());
        checkSupertypeVariance(that.getSatisfiedTypes(), that.getDeclarationModel());
    }

}